import { useState, useEffect, useRef, useCallback } from "react";
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import { Api } from "../utils/api";
import { CONFIG } from "../utils/config";
import { formatTs } from "../utils/helpers";

export const useChat = (username, logout) => {
  const [messages, setMessages] = useState([]);
  const [onlineUsers, setOnlineUsers] = useState(new Map());
  const [connState, setConnState] = useState("connecting");
  const [typers, setTypers] = useState(new Set());
  const [isOverlayVisible, setOverlayVisible] = useState(false);

  const stompClient = useRef(null);
  const reconnectTries = useRef(0);
  const reconnectTimer = useRef(null);
  const typingTimeouts = useRef(new Map());

  const hasLoadedHistory = useRef(false); //mark history chat

  const addMessage = useCallback((msg) => {
    setMessages((prev) => [
      ...prev,
      { ...msg, internalId: Date.now() + Math.random() },
    ]);
  }, []);

  const handleTyping = useCallback(
    (sender, isTyping) => {
      if (sender === username) return;
      setTypers((prev) => {
        const next = new Set(prev);
        if (isTyping) {
          next.add(sender);
          if (typingTimeouts.current.has(sender))
            clearTimeout(typingTimeouts.current.get(sender));
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
    [username],
  );

  const connect = useCallback(async () => {
    try {
      if (!hasLoadedHistory.current) {
        const history = await Api.getHistory();
        const formatted = (history.content || []).reverse().map((m) => ({
          type: "CHAT",
          sender: m.senderUsername,
          content: m.content,
          timestamp: m.timestamp,
          time: formatTs(m.timestamp),
          internalId: m.id,
        }));
        setMessages(formatted);
        hasLoadedHistory.current = true;
      }
    } catch (e) {
      console.error("Lỗi tải lịch sử", e);
    }

    setConnState("connecting");
    const sock = new SockJS(`${CONFIG.WS_BASE_URL}${CONFIG.WS_ENDPOINT}`);
    const client = Stomp.over(sock);
    client.debug = () => {};
    client.heartbeat.outgoing = 10000;
    client.heartbeat.incoming = 10000;

    client.connect(
      { Authorization: `Bearer ${Api.getToken()}` },
      () => {
        setConnState("connected");
        setOverlayVisible(false);
        reconnectTries.current = 0;

        client.subscribe(CONFIG.WS_TOPIC, (frame) => {
          const msg = JSON.parse(frame.body);
          const now = new Date().toISOString();

          if (msg.type === "JOIN") {
            setOnlineUsers((prev) => new Map(prev).set(msg.sender, true));
            addMessage({ ...msg, timestamp: now, time: formatTs(now) });
          } else if (msg.type === "LEAVE") {
            setOnlineUsers((prev) => {
              const n = new Map(prev);
              n.delete(msg.sender);
              return n;
            });
            addMessage({ ...msg, timestamp: now, time: formatTs(now) });
          } else if (msg.type === "TYPING") {
            handleTyping(msg.sender, msg.content === "true");
          } else {
            const msgTimestamp = msg.timestamp || now;
            addMessage({
              ...msg,
              timestamp: msgTimestamp,
              time: formatTs(msgTimestamp),
            });
            handleTyping(msg.sender, false);
          }
        });

        client.send(
          CONFIG.WS_JOIN,
          {},
          JSON.stringify({ sender: username, type: "JOIN" }),
        );
        setOnlineUsers((prev) => new Map(prev).set(username, true));
      },
      (err) => {
        const errorMsg =
          typeof err === "string" ? err : err?.headers?.message || "";
        if (
          errorMsg.includes("INVALID_TOKEN") ||
          errorMsg.includes("UNAUTHORIZED")
        ) {
          logout();
          return;
        }
        setConnState("disconnected");
        if (reconnectTries.current < CONFIG.MAX_RECONNECT_TRIES) {
          reconnectTries.current++;
          setOverlayVisible(true);
          reconnectTimer.current = setTimeout(
            connect,
            CONFIG.RECONNECT_DELAY_MS,
          );
        }
      },
    );
    stompClient.current = client;
  }, [username, logout, addMessage, handleTyping]);

  useEffect(() => {
    connect();
    return () => {
      clearTimeout(reconnectTimer.current);
      if (stompClient.current && stompClient.current.connected) {
        stompClient.current.send(
          CONFIG.WS_REMOVE,
          {},
          JSON.stringify({ sender: username }),
        );
        stompClient.current.disconnect();
      }
    };
  }, [connect, username]);

  const sendMessage = useCallback(
    (content) => {
      if (stompClient.current?.connected && content.trim()) {
        stompClient.current.send(
          CONFIG.WS_SEND,
          { Authorization: `Bearer ${Api.getToken()}` },
          JSON.stringify({ sender: username, content, type: "CHAT" }),
        );
      }
    },
    [username],
  );

  const sendTyping = useCallback(
    (isTyping) => {
      if (stompClient.current?.connected) {
        stompClient.current.send(
          CONFIG.WS_TYPING,
          { Authorization: `Bearer ${Api.getToken()}` },
          JSON.stringify({
            sender: username,
            type: "TYPING",
            content: isTyping.toString(),
          }),
        );
      }
    },
    [username],
  );

  return {
    messages,
    onlineUsers,
    connState,
    typers,
    isOverlayVisible,
    sendMessage,
    sendTyping,
  };
};
