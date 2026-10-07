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
  } catch (networkErr) {
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
  isLoggedIn: () => !!getToken(),
  getToken,
  getStoredUsername: () => localStorage.getItem(CONFIG.USERNAME_KEY) || "",
  saveSession: (token, username) => {
    localStorage.setItem(CONFIG.TOKEN_KEY, token);
    if (username) localStorage.setItem(CONFIG.USERNAME_KEY, username);
  },
  clearSession: handleUnauthorized,
};
