import { CONFIG } from "./config";

const getToken = () => localStorage.getItem(CONFIG.TOKEN_KEY);

const authHeaders = () => {
  const token = getToken();
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
};

const handleUnauthorized = () => {
  localStorage.removeItem(CONFIG.TOKEN_KEY);
  localStorage.removeItem(CONFIG.USERNAME_KEY);
  window.dispatchEvent(new Event("auth-expired"));
};

const request = async (method, path, body = null) => {
  const opts = {
    method,
    headers: authHeaders(),
    mode: "cors",
  };
  if (body) opts.body = JSON.stringify(body);

  let res;
  try {
    res = await fetch(`${CONFIG.API_BASE_URL}${path}`, opts);
  } catch (_networkErr) {
    throw new Error("Không thể kết nối đến server. Vui lòng thử lại.");
  }

  if (res.status === 401) {
    handleUnauthorized();
    throw new Error("Phiên đăng nhập đã hết hạn.");
  }

  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(json.message || json.error || `HTTP ${res.status}`);
  }
  return json;
};

export const Api = {
  login: async (username, password) => {
    const res = await request("POST", "/api/v1/auth/login", {
      username,
      password,
    });
    return res.data;
  },
  register: async (username, email, password, repeatPassword) => {
    const res = await request("POST", "/api/v1/auth/register", {
      username,
      email,
      password,
      repeatPassword,
    });
    return res.data;
  },
  getHistory: async (page = 0, size = CONFIG.HISTORY_PAGE_SIZE) => {
    const res = await request(
      "GET",
      `/api/v1/chat/history/page?page=${page}&size=${size}`,
    );
    return res.data;
  },
  searchUsers: async (query, page = 0, size = 20, signal) => {
    const opts = {
      method: "GET",
      headers: authHeaders(),
      mode: "cors",
    };
    if (signal) opts.signal = signal;

    let res;
    try {
      res = await fetch(
        `${CONFIG.API_BASE_URL}/api/v1/users/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`,
        opts,
      );
    } catch (err) {
      if (err.name === "AbortError") throw err;
      throw new Error("Không thể kết nối đến server. Vui lòng thử lại.");
    }

    if (res.status === 401) {
      handleUnauthorized();
      throw new Error("Phiên đăng nhập đã hết hạn.");
    }

    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(json.message || json.error || `HTTP ${res.status}`);
    }
    return json.data;
  },
  getUserProfile: async (userId) => {
    const res = await request("GET", `/api/v1/users/${userId}`);
    return res.data;
  },
  sendFriendRequest: async (receiverId) => {
    const res = await request("POST", "/api/v1/friend-requests", { receiverId });
    return res.data;
  },
  getReceivedRequests: async (page = 0, size = 20) => {
    const res = await request("GET", `/api/v1/friend-requests/received?page=${page}&size=${size}`);
    return res.data;
  },
  getSentRequests: async (page = 0, size = 20) => {
    const res = await request("GET", `/api/v1/friend-requests/sent?page=${page}&size=${size}`);
    return res.data;
  },
  acceptRequest: async (id) => {
    const res = await request("PATCH", `/api/v1/friend-requests/${id}/accept`);
    return res.data;
  },
  rejectRequest: async (id) => {
    const res = await request("PATCH", `/api/v1/friend-requests/${id}/reject`);
    return res.data;
  },
  cancelRequest: async (id) => {
    const res = await request("DELETE", `/api/v1/friend-requests/${id}`);
    return res.data;
  },
  getFriends: async (page = 0, size = 20) => {
    const res = await request("GET", `/api/v1/friends?page=${page}&size=${size}`);
    return res.data;
  },
  getConversations: async (page = 0, size = 20) => {
    const res = await request("GET", `/api/v1/conversations?page=${page}&size=${size}`);
    return res.data;
  },
  getConversation: async (conversationId) => {
    const res = await request("GET", `/api/v1/conversations/${conversationId}`);
    return res.data;
  },
  getOrCreateDirectConversation: async (targetUserId) => {
    const res = await request("POST", "/api/v1/conversations/direct", { targetUserId });
    return res.data;
  },
  createGroup: async (name, memberUserIds) => {
    const res = await request("POST", "/api/v1/conversations/group", {
      name,
      memberUserIds,
    });
    return res.data;
  },
  getGroupMembers: async (conversationId) => {
    const res = await request("GET", `/api/v1/conversations/${conversationId}/members`);
    return res.data;
  },
  addGroupMembers: async (conversationId, memberUserIds) => {
    const res = await request("POST", `/api/v1/conversations/${conversationId}/members`, {
      memberUserIds,
    });
    return res.data;
  },
  removeGroupMember: async (conversationId, userId) => {
    const res = await request("DELETE", `/api/v1/conversations/${conversationId}/members/${userId}`);
    return res.data;
  },
  leaveGroup: async (conversationId, newAdminUserId = null) => {
    const body = newAdminUserId ? { newAdminUserId } : {};
    const res = await request("POST", `/api/v1/conversations/${conversationId}/leave`, body);
    return res.data;
  },
  transferAdmin: async (conversationId, newAdminUserId) => {
    const res = await request("PATCH", `/api/v1/conversations/${conversationId}/admin`, {
      newAdminUserId,
    });
    return res.data;
  },
  updateGroup: async (conversationId, { name, avatar }) => {
    const body = {};
    if (name !== undefined) body.name = name;
    if (avatar !== undefined) body.avatar = avatar;
    const res = await request("PATCH", `/api/v1/conversations/${conversationId}`, body);
    return res.data;
  },
  getConversationHistory: async (conversationId, page = 0, size = CONFIG.HISTORY_PAGE_SIZE) => {
    const res = await request(
      "GET",
      `/api/v1/chat/${conversationId}/messages?page=${page}&size=${size}`
    );
    return res.data;
  },
  markConversationAsRead: async (conversationId, messageId = null) => {
    const res = await request(
      "POST",
      `/api/v1/conversations/${conversationId}/read`,
      { messageId }
    );
    return res.data;
  },
  getPresence: async (usernames = null) => {
    const query = usernames && usernames.length > 0 ? `?usernames=${encodeURIComponent(usernames.join(','))}` : '';
    const res = await request("GET", `/api/v1/users/presence${query}`);
    return res.data;
  },
  isLoggedIn: () => !!getToken(),
  getToken,
  getStoredUsername: () => localStorage.getItem(CONFIG.USERNAME_KEY) || "",
  saveSession: (token, username) => {
    localStorage.setItem(CONFIG.TOKEN_KEY, token);
    if (username) localStorage.setItem(CONFIG.USERNAME_KEY, username);
  },
  clearSession: handleUnauthorized,
};
