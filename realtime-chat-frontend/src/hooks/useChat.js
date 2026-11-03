import { useState, useEffect, useRef, useCallback } from "react";
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import { Api } from "../utils/api";
import { CONFIG, getWsUrl } from "../utils/config";
import { formatTs } from "../utils/helpers";
import { showToast } from "../utils/toast";

export const useChat = (username, logout, urlConversationId, onNavigate) => {
  const [conversations, setConversations] = useState([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [conversationError, setConversationError] = useState(null);

  const [activeConversationId, setActiveConversationId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [loadingMessages, setLoadingMessages] = useState(false);

  const [onlineUsers, setOnlineUsers] = useState(new Map());
  const [lastSeenUsers, setLastSeenUsers] = useState(new Map());
  const [connState, setConnState] = useState("connecting");
  const [typers, setTypers] = useState(new Set());
  const [isOverlayVisible, setOverlayVisible] = useState(false);

  const stompClient = useRef(null);
  const activeSubRef = useRef(null);
  const publicSubRef = useRef(null);
  const presenceSubRef = useRef(null);
  const userSubRef = useRef(null);
  const userErrorsSubRef = useRef(null);
  const reconnectTries = useRef(0);
  const reconnectTimer = useRef(null);
  const typingTimeouts = useRef(new Map());
  const historyRequestId = useRef(0);
  const presenceEventTimestampsRef = useRef(new Map());

  const connStateRef = useRef(connState);
  useEffect(() => {
    connStateRef.current = connState;
  }, [connState]);

  const selectConversationRef = useRef(null);

  const urlConversationIdRef = useRef(urlConversationId);
  useEffect(() => {
    urlConversationIdRef.current = urlConversationId;
  }, [urlConversationId]);

  const activeConversationIdRef = useRef(activeConversationId);
  useEffect(() => {
    activeConversationIdRef.current = activeConversationId;
  }, [activeConversationId]);

  const conversationsRef = useRef(conversations);
  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  const onNavigateRef = useRef(onNavigate);
  useEffect(() => {
    onNavigateRef.current = onNavigate;
  }, [onNavigate]);

  const logoutRef = useRef(logout);
  useEffect(() => {
    logoutRef.current = logout;
  }, [logout]);

  const usernameRef = useRef(username);
  useEffect(() => {
    usernameRef.current = username;
  }, [username]);

  const isConnectingRef = useRef(false);
  const isMountedRef = useRef(true);

  const markAsRead = useCallback(async (convId, messageId = null) => {
    if (!convId) return;
    try {
      const res = await Api.markConversationAsRead(convId, messageId);
      setConversations((prev) =>
        prev.map((c) =>
          c.id === convId
            ? {
                ...c,
                unreadCount: 0,
                lastReadMessageId: res?.lastReadMessageId ?? c.lastReadMessageId,
              }
            : c
        )
      );
      return res;
    } catch (e) {
      console.warn("Lỗi đánh dấu đã đọc:", e);
    }
  }, []);

  // Update conversation snippet and re-order to top
  const updateConversationPreview = useCallback((convId, content, sender, timestamp) => {
    if (!convId) return;
    const ts = timestamp || new Date().toISOString();
    setConversations((prev) => {
      const list = [...prev];
      const idx = list.findIndex((c) => c.id === convId);
      if (idx === -1) return prev;
      const updated = {
        ...list[idx],
        lastMessage: content,
        lastMessageSender: sender,
        lastMessageTimestamp: ts,
        lastActivityAt: ts,
      };
      list.splice(idx, 1);
      return [updated, ...list];
    });
  }, []);

  const addMessage = useCallback((msg, convId) => {
    if (convId === activeConversationIdRef.current) {
      setMessages((prev) => {
        // Deduplication check
        if (msg.id && prev.some((m) => m.id === msg.id)) {
          return prev;
        }
        return [
          ...prev,
          {
            ...msg,
            internalId: msg.id || Date.now() + Math.random(),
          },
        ];
      });
    }
  }, []);

  const handleTyping = useCallback(
    (sender, isTyping) => {
      if (sender === usernameRef.current) return;
      setTypers((prev) => {
        const next = new Set(prev);
        if (isTyping) {
          next.add(sender);
          if (typingTimeouts.current.has(sender)) {
            clearTimeout(typingTimeouts.current.get(sender));
          }
          typingTimeouts.current.set(
            sender,
            setTimeout(() => handleTyping(sender, false), 3000),
          );
        } else {
          next.delete(sender);
          clearTimeout(typingTimeouts.current.get(sender));
          typingTimeouts.current.delete(sender);
        }
        return next;
      });
    },
    [],
  );

  const loadInitialPresence = useCallback(async () => {
    try {
      const presenceList = await Api.getPresence();
      if (Array.isArray(presenceList)) {
        setOnlineUsers((prev) => {
          const map = new Map(prev);
          presenceList.forEach((p) => {
            const recordedTs = presenceEventTimestampsRef.current.get(p.username);
            // Only apply snapshot if we haven't processed a newer realtime event
            if (!recordedTs) {
              if (p.online) {
                map.set(p.username, true);
              } else {
                map.delete(p.username);
              }
            }
          });
          return map;
        });
        setLastSeenUsers((prev) => {
          const map = new Map(prev);
          presenceList.forEach((p) => {
            const recordedTs = presenceEventTimestampsRef.current.get(p.username);
            if (!recordedTs && p.lastSeenAt) {
              map.set(p.username, p.lastSeenAt);
            }
          });
          return map;
        });
      }
    } catch (e) {
      console.warn("Lỗi nạp presence ban đầu:", e);
    }
  }, []);

  const loadConversations = useCallback(async () => {
    setLoadingConversations(true);
    setConversationError(null);
    try {
      const data = await Api.getConversations(0, 50);
      const list = data?.content || (Array.isArray(data) ? data : []);
      setConversations(list);

      // Đồng bộ trạng thái online/lastSeen từ metadata bạn bè trong danh sách
      setOnlineUsers((prev) => {
        const map = new Map(prev);
        list.forEach((c) => {
          if (c.otherUser?.username && c.otherUser.isOnline) {
            map.set(c.otherUser.username, true);
          }
        });
        return map;
      });
      setLastSeenUsers((prev) => {
        const map = new Map(prev);
        list.forEach((c) => {
          if (c.otherUser?.username && c.otherUser.lastSeenAt) {
            map.set(c.otherUser.username, c.otherUser.lastSeenAt);
          }
        });
        return map;
      });

      return list;
    } catch (err) {
      console.error("Failed to load conversations", err);
      setConversationError(err.message || "Không thể tải danh sách hội thoại");
      return [];
    } finally {
      setLoadingConversations(false);
    }
  }, []);

  const loadHistory = useCallback(async (convId) => {
    if (!convId) return;
    historyRequestId.current += 1;
    const reqId = historyRequestId.current;
    setLoadingMessages(true);

    try {
      const history = await Api.getConversationHistory(convId);
      // Race condition check: only set if this is still the active conversation and latest request
      if (
        historyRequestId.current === reqId &&
        activeConversationIdRef.current === convId
      ) {
        const formatted = (history?.content || []).reverse().map((m) => ({
          id: m.id,
          type: "CHAT",
          sender: m.senderUsername,
          content: m.content,
          timestamp: m.timestamp,
          time: formatTs(m.timestamp),
          internalId: m.id,
        }));
        setMessages(formatted);
        const latestMsg = formatted[formatted.length - 1];
        markAsRead(convId, latestMsg?.id || null);
      }
    } catch (e) {
      if (historyRequestId.current === reqId) {
        console.error("Lỗi tải lịch sử trò chuyện:", e);
        const errorMsg = e.message || "";
        if (errorMsg.includes("not an active member") || errorMsg.includes("Access denied") || errorMsg.includes("403")) {
          showToast("Bạn không có quyền truy cập cuộc trò chuyện này.", "error");
          setConversations((prev) => prev.filter((c) => c.id !== convId));
          const publicConv = conversationsRef.current.find((c) => c.type === "PUBLIC");
          if (publicConv) {
            selectConversationRef.current?.(publicConv.id, true);
          } else if (onNavigateRef.current) {
            onNavigateRef.current(null);
          }
        } else {
          showToast(e.message || "Không thể tải lịch sử trò chuyện", "error");
        }
      }
    } finally {
      if (historyRequestId.current === reqId) {
        setLoadingMessages(false);
      }
    }
  }, [markAsRead]);

  // Subscribe to the active conversation only
  const subscribeToActiveConversation = useCallback(
    (convId) => {
      if (!stompClient.current?.connected || !convId) return;

      // Clean up old active subscription
      if (activeSubRef.current) {
        try { activeSubRef.current.unsubscribe(); } catch (_) {}
        activeSubRef.current = null;
      }

      const topic = `/topic/conversation.${convId}`;
      const sub = stompClient.current.subscribe(topic, (frame) => {
        try {
          const msg = JSON.parse(frame.body);
          const now = new Date().toISOString();

          if (msg.type === "TYPING") {
            if (convId === activeConversationIdRef.current) {
              handleTyping(msg.sender, msg.content === "true");
            }
          } else if (msg.type === "READ_RECEIPT") {
            if (msg.readerUsername !== usernameRef.current) {
              setConversations((prev) =>
                prev.map((c) =>
                  c.id === convId
                    ? { ...c, otherLastReadMessageId: msg.lastReadMessageId }
                    : c
                )
              );
            }
          } else if (msg.type === "CHAT") {
            const msgTimestamp = msg.timestamp || now;
            addMessage(
              {
                ...msg,
                timestamp: msgTimestamp,
                time: formatTs(msgTimestamp),
              },
              convId,
            );
            updateConversationPreview(convId, msg.content, msg.sender, msgTimestamp);
            if (convId === activeConversationIdRef.current) {
              handleTyping(msg.sender, false);
              if (msg.sender !== usernameRef.current && document.visibilityState === "visible") {
                markAsRead(convId, msg.id || null);
              }
            }
          }
        } catch (e) {
          console.error("Error parsing WS message:", e);
        }
      });

      activeSubRef.current = sub;
    },
    [addMessage, handleTyping, updateConversationPreview, markAsRead],
  );

  // Switch conversation with clean transition
  const selectConversation = useCallback(
    async (convId, pushUrl = true) => {
      if (!convId || convId === activeConversationIdRef.current) return;

      // Clean up previous subscription immediately
      if (activeSubRef.current) {
        try { activeSubRef.current.unsubscribe(); } catch (_) {}
        activeSubRef.current = null;
      }

      setActiveConversationId(convId);
      setMessages([]);
      setTypers(new Set());

      // Optimistically clear unread count for selected conversation
      setConversations((prev) =>
        prev.map((c) => (c.id === convId ? { ...c, unreadCount: 0 } : c))
      );

      if (pushUrl && onNavigateRef.current) {
        onNavigateRef.current(convId);
      }

      // Load history and mark as read
      loadHistory(convId);

      // Subscribe to new topic
      subscribeToActiveConversation(convId);
    },
    [loadHistory, subscribeToActiveConversation],
  );

  useEffect(() => {
    selectConversationRef.current = selectConversation;
  }, [selectConversation]);

  // WebSocket connect
  const connect = useCallback(async () => {
    const curUser = usernameRef.current || username;
    const curToken = Api.getToken();
    if (!curUser || !curToken) return;

    if (isConnectingRef.current) return;
    isConnectingRef.current = true;

    setConnState("connecting");

    // Clean up previous client if any
    if (stompClient.current) {
      try {
        if (typeof stompClient.current.deactivate === "function") {
          stompClient.current.deactivate();
        } else if (typeof stompClient.current.disconnect === "function") {
          stompClient.current.disconnect();
        }
      } catch (_) {}
      stompClient.current = null;
    }

    let convs = [];
    try {
      convs = await loadConversations();
    } catch (e) {
      console.warn("Failed to load conversations on connect:", e);
    }

    if (!isMountedRef.current) {
      isConnectingRef.current = false;
      return;
    }

    let targetConvId = activeConversationIdRef.current;
    if (!targetConvId && urlConversationIdRef.current) {
      const parsedUrlId = Number(urlConversationIdRef.current);
      try {
        const verified = await Api.getConversation(parsedUrlId);
        targetConvId = verified.id;
      } catch (_err) {
        showToast("Bạn không có quyền truy cập cuộc trò chuyện này.", "error");
        if (onNavigateRef.current) onNavigateRef.current(null);
      }
    }

    if (!targetConvId && convs && convs.length > 0) {
      const publicConv = convs.find((c) => c.type === "PUBLIC");
      targetConvId = (publicConv || convs[0]).id;
    }

    if (targetConvId) {
      setActiveConversationId(targetConvId);
      loadHistory(targetConvId);
    }

    const wsUrl = getWsUrl();
    const client = Stomp.over(() => new SockJS(wsUrl));
    client.debug = () => {};
    client.heartbeat.outgoing = 10000;
    client.heartbeat.incoming = 10000;
    client.reconnectDelay = 0;

    const token = Api.getToken();
    if (!token) {
      isConnectingRef.current = false;
      return;
    }

    client.connect(
      { Authorization: `Bearer ${token}` },
      () => {
        isConnectingRef.current = false;
        if (!isMountedRef.current) {
          try {
            if (typeof client.deactivate === "function") client.deactivate();
            else if (typeof client.disconnect === "function") client.disconnect();
          } catch (_) {}
          return;
        }

        setConnState("connected");
        setOverlayVisible(false);
        reconnectTries.current = 0;

        // 1. Subscribe /topic/presence FIRST so no streaming events are missed
        if (presenceSubRef.current) {
          try { presenceSubRef.current.unsubscribe(); } catch (_) {}
        }
        presenceSubRef.current = client.subscribe(CONFIG.WS_PRESENCE_TOPIC || "/topic/presence", (frame) => {
          try {
            const event = JSON.parse(frame.body);
            if (event && event.username) {
              const eventTs = event.timestamp ? new Date(event.timestamp).getTime() : Date.now();
              const recordedTs = presenceEventTimestampsRef.current.get(event.username) || 0;
              if (eventTs >= recordedTs) {
                presenceEventTimestampsRef.current.set(event.username, eventTs);
                if (event.online) {
                  setOnlineUsers((prev) => new Map(prev).set(event.username, true));
                } else {
                  setOnlineUsers((prev) => {
                    const next = new Map(prev);
                    next.delete(event.username);
                    return next;
                  });
                  if (event.lastSeenAt) {
                    setLastSeenUsers((prev) => new Map(prev).set(event.username, event.lastSeenAt));
                  }
                }
              }
            }
          } catch (e) {
            console.error("Lỗi parse sự kiện presence:", e);
          }
        });

        // 2. Fetch baseline REST presence snapshot immediately AFTER subscription is established
        loadInitialPresence();

        // 3. Subscribe to /topic/public for global chat messages
        if (publicSubRef.current) {
          try { publicSubRef.current.unsubscribe(); } catch (_) {}
        }
        publicSubRef.current = client.subscribe("/topic/public", (frame) => {
          try {
            const msg = JSON.parse(frame.body);
            const now = new Date().toISOString();
            const publicConv = conversationsRef.current.find((c) => c.type === "PUBLIC");
            if (publicConv && (msg.conversationId == null || Number(msg.conversationId) === Number(publicConv.id))) {
              if (msg.type === "CHAT") {
                addMessage(
                  {
                    ...msg,
                    timestamp: msg.timestamp || now,
                    time: formatTs(msg.timestamp || now),
                  },
                  publicConv.id,
                );
                updateConversationPreview(publicConv.id, msg.content, msg.sender, msg.timestamp || now);
              }
            }
          } catch (e) {
            console.error("Error in public topic listener:", e);
          }
        });

        // 4. Subscribe to the active conversation
        const curActiveId = activeConversationIdRef.current || targetConvId;
        if (curActiveId) {
          subscribeToActiveConversation(curActiveId);
        }

        // 5. Subscribe to user-specific events queue (/user/queue/events)
        if (userSubRef.current) {
          try { userSubRef.current.unsubscribe(); } catch (_) {}
        }
        userSubRef.current = client.subscribe("/user/queue/events", (frame) => {
          try {
            const event = JSON.parse(frame.body);
            if (event.type === "CONVERSATION_UPDATE") {
              const convId = event.conversationId;
              const isViewingActive =
                convId === activeConversationIdRef.current &&
                document.visibilityState === "visible";

              setConversations((prev) => {
                const list = [...prev];
                const idx = list.findIndex((c) => c.id === convId);
                if (idx === -1) {
                  loadConversations();
                  return prev;
                }
                const current = list[idx];
                const unread = isViewingActive ? 0 : (event.unreadCount ?? current.unreadCount);
                const updated = {
                  ...current,
                  lastMessage: event.lastMessage,
                  lastMessageSender: event.lastMessageSender,
                  lastMessageTimestamp: event.lastMessageTimestamp,
                  lastActivityAt: event.lastActivityAt || event.lastMessageTimestamp,
                  unreadCount: unread,
                };
                list.splice(idx, 1);
                return [updated, ...list];
              });

              if (isViewingActive && event.lastMessageSender !== usernameRef.current) {
                markAsRead(convId, event.messageId);
              }
            } else if (event.type === "READ_RECEIPT") {
              const convId = event.conversationId;
              setConversations((prev) =>
                prev.map((c) => {
                  if (c.id !== convId) return c;
                  if (event.readerUsername === usernameRef.current) {
                    return {
                      ...c,
                      unreadCount: 0,
                      lastReadMessageId: event.lastReadMessageId,
                    };
                  } else {
                    return {
                      ...c,
                      otherLastReadMessageId: event.lastReadMessageId,
                    };
                  }
                })
              );
            } else if (event.type === "MEMBER_KICKED") {
              const convId = event.conversationId;
              setConversations((prev) => prev.filter((c) => c.id !== convId));
              if (activeConversationIdRef.current === convId) {
                if (activeSubRef.current) {
                  try { activeSubRef.current.unsubscribe(); } catch (_) {}
                  activeSubRef.current = null;
                }
                showToast("Bạn đã bị xóa khỏi nhóm trò chuyện.", "info");
                const publicConv = conversationsRef.current.find((c) => c.type === "PUBLIC");
                if (publicConv) {
                  selectConversationRef.current?.(publicConv.id, true);
                } else if (onNavigateRef.current) {
                  onNavigateRef.current(null);
                }
              }
            }
          } catch (e) {
            console.error("Error in user events listener:", e);
          }
        });

        // 6. Subscribe to user-specific errors queue (/user/queue/errors)
        if (userErrorsSubRef.current) {
          try { userErrorsSubRef.current.unsubscribe(); } catch (_) {}
        }
        userErrorsSubRef.current = client.subscribe("/user/queue/errors", (frame) => {
          try {
            const errData = JSON.parse(frame.body);
            if (errData && errData.error) {
              console.warn("Server message error:", errData.error);
              showToast(errData.error, "error");
            }
          } catch (e) {
            console.error("Error in user errors listener:", e);
          }
        });
      },
      (err) => {
        isConnectingRef.current = false;
        const errorMsg =
          typeof err === "string" ? err : err?.headers?.message || "";
        const wasConnected = connStateRef.current === "connected";

        if (
          errorMsg.includes("INVALID_TOKEN") ||
          (!wasConnected && errorMsg.includes("UNAUTHORIZED"))
        ) {
          logoutRef.current?.();
          return;
        }

        if (wasConnected && (errorMsg.includes("UNAUTHORIZED") || errorMsg.includes("FORBIDDEN"))) {
          console.warn("WebSocket authorization error:", errorMsg);
          showToast("Bạn không có quyền truy cập cuộc trò chuyện này.", "error");
          const publicConv = conversationsRef.current.find((c) => c.type === "PUBLIC");
          if (publicConv) {
            selectConversationRef.current?.(publicConv.id, true);
          }
          return;
        }

        if (!isMountedRef.current) return;

        setConnState("reconnecting");
        if (reconnectTries.current < CONFIG.MAX_RECONNECT_TRIES) {
          reconnectTries.current++;
          setOverlayVisible(true);
          clearTimeout(reconnectTimer.current);
          reconnectTimer.current = setTimeout(
            () => {
              isConnectingRef.current = false;
              connect();
            },
            CONFIG.RECONNECT_DELAY_MS,
          );
        } else {
          setConnState("disconnected");
        }
      },
    );
    stompClient.current = client;
  }, [
    username,
    loadConversations,
    loadInitialPresence,
    loadHistory,
    addMessage,
    updateConversationPreview,
    subscribeToActiveConversation,
    markAsRead,
  ]);

  useEffect(() => {
    isMountedRef.current = true;
    connect();
    return () => {
      isMountedRef.current = false;
      isConnectingRef.current = false;
      clearTimeout(reconnectTimer.current);
      if (activeSubRef.current) {
        try { activeSubRef.current.unsubscribe(); } catch (_) {}
        activeSubRef.current = null;
      }
      if (publicSubRef.current) {
        try { publicSubRef.current.unsubscribe(); } catch (_) {}
        publicSubRef.current = null;
      }
      if (presenceSubRef.current) {
        try { presenceSubRef.current.unsubscribe(); } catch (_) {}
        presenceSubRef.current = null;
      }
      if (userSubRef.current) {
        try { userSubRef.current.unsubscribe(); } catch (_) {}
        userSubRef.current = null;
      }
      if (userErrorsSubRef.current) {
        try { userErrorsSubRef.current.unsubscribe(); } catch (_) {}
        userErrorsSubRef.current = null;
      }
      if (stompClient.current) {
        try {
          if (typeof stompClient.current.deactivate === "function") {
            stompClient.current.deactivate();
          } else if (typeof stompClient.current.disconnect === "function") {
            stompClient.current.disconnect();
          }
        } catch (_) {}
        stompClient.current = null;
      }
    };
  }, [connect]);

  // Handle URL conversation change when already mounted
  useEffect(() => {
    if (!urlConversationId) return;
    const parsedId = Number(urlConversationId);
    if (parsedId && parsedId !== activeConversationIdRef.current) {
      // Validate access
      Api.getConversation(parsedId)
        .then((conv) => {
          selectConversation(conv.id, false);
        })
        .catch(() => {
          showToast("Bạn không có quyền truy cập cuộc trò chuyện này.", "error");
          if (onNavigateRef.current) onNavigateRef.current(null);
        });
    }
  }, [urlConversationId, selectConversation]);

  const createDirectChat = useCallback(
    async (targetUserId) => {
      try {
        const conv = await Api.getOrCreateDirectConversation(targetUserId);
        setConversations((prev) => {
          const exists = prev.find((c) => c.id === conv.id);
          if (exists) {
            return [conv, ...prev.filter((c) => c.id !== conv.id)];
          }
          return [conv, ...prev];
        });
        await selectConversation(conv.id, true);
        return conv;
      } catch (e) {
        console.error("Error creating direct chat:", e);
        showToast(e.message || "Không thể tạo cuộc trò chuyện", "error");
        throw e;
      }
    },
    [selectConversation],
  );

  const createGroup = useCallback(
    async (name, memberUserIds) => {
      try {
        const conv = await Api.createGroup(name, memberUserIds);
        setConversations((prev) => {
          const exists = prev.find((c) => c.id === conv.id);
          if (exists) {
            return [conv, ...prev.filter((c) => c.id !== conv.id)];
          }
          return [conv, ...prev];
        });
        await selectConversation(conv.id, true);
        showToast("Tạo nhóm thành công!", "success");
        return conv;
      } catch (e) {
        console.error("Error creating group:", e);
        showToast(e.message || "Không thể tạo nhóm", "error");
        throw e;
      }
    },
    [selectConversation],
  );

  const updateGroup = useCallback(
    async (convId, updateData) => {
      try {
        const updated = await Api.updateGroup(convId, updateData);
        setConversations((prev) =>
          prev.map((c) => (c.id === convId ? { ...c, ...updated } : c))
        );
        showToast("Cập nhật thông tin nhóm thành công!", "success");
        return updated;
      } catch (e) {
        console.error("Error updating group:", e);
        showToast(e.message || "Không thể cập nhật thông tin nhóm", "error");
        throw e;
      }
    },
    [],
  );

  const leaveGroup = useCallback(
    async (convId, newAdminUserId = null) => {
      try {
        await Api.leaveGroup(convId, newAdminUserId);
        setConversations((prev) => prev.filter((c) => c.id !== convId));
        showToast("Đã rời khỏi nhóm", "info");
        if (convId === activeConversationIdRef.current) {
          const publicConv = conversationsRef.current.find((c) => c.type === "PUBLIC");
          if (publicConv) {
            selectConversation(publicConv.id, true);
          } else if (onNavigateRef.current) {
            onNavigateRef.current(null);
          }
        }
      } catch (e) {
        console.error("Error leaving group:", e);
        showToast(e.message || "Không thể rời nhóm", "error");
        throw e;
      }
    },
    [selectConversation],
  );

  const updateMemberCount = useCallback((convId, count) => {
    if (!convId || count == null) return;
    setConversations((prev) =>
      prev.map((c) => (c.id === convId ? { ...c, memberCount: count } : c))
    );
  }, []);

  const sendMessage = useCallback(
    (content) => {
      const convId = activeConversationIdRef.current;
      if (
        stompClient.current?.connected &&
        content.trim() &&
        convId
      ) {
        stompClient.current.send(
          CONFIG.WS_SEND,
          { Authorization: `Bearer ${Api.getToken()}` },
          JSON.stringify({
            sender: usernameRef.current,
            content,
            type: "CHAT",
            conversationId: convId,
          }),
        );
      }
    },
    [],
  );

  const sendTyping = useCallback(
    (isTyping) => {
      const convId = activeConversationIdRef.current;
      if (stompClient.current?.connected && convId) {
        stompClient.current.send(
          CONFIG.WS_TYPING,
          { Authorization: `Bearer ${Api.getToken()}` },
          JSON.stringify({
            sender: usernameRef.current,
            type: "TYPING",
            content: isTyping.toString(),
            conversationId: convId,
          }),
        );
      }
    },
    [],
  );

  // Mark active conversation as read when returning to tab
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible" && activeConversationIdRef.current) {
        const activeConv = conversationsRef.current.find(
          (c) => c.id === activeConversationIdRef.current
        );
        if (activeConv && activeConv.unreadCount > 0) {
          markAsRead(activeConversationIdRef.current);
        }
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("focus", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("focus", handleVisibilityChange);
    };
  }, [markAsRead]);

  return {
    conversations,
    loadingConversations,
    conversationError,
    loadConversations,
    activeConversationId,
    selectConversation,
    createDirectChat,
    createGroup,
    updateGroup,
    leaveGroup,
    updateMemberCount,
    messages,
    loadingMessages,
    onlineUsers,
    lastSeenUsers,
    connState,
    typers,
    isOverlayVisible,
    sendMessage,
    sendTyping,
    markAsRead,
  };
};
