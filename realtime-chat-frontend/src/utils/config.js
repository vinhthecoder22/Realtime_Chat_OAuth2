export const CONFIG = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL ?? "",
  WS_BASE_URL: import.meta.env.VITE_WS_BASE_URL ?? "",
  TOKEN_KEY: "chatweb_token",
  USERNAME_KEY: "chatweb_username",
  WS_ENDPOINT: "/ws-chat",
  WS_TOPIC: "/topic/public",
  WS_PRESENCE_TOPIC: "/topic/presence",
  WS_SEND: "/app/chat.sendMessage",
  WS_JOIN: "/app/chat.addUser",
  WS_REMOVE: "/app/chat.removeUser",
  WS_TYPING: "/app/chat.typing",
  RECONNECT_DELAY_MS: 4000,
  MAX_RECONNECT_TRIES: 5,
  MSG_MAX_LENGTH: 2000,
  HISTORY_PAGE_SIZE: 50,
};

export const getWsUrl = () => {
  let base = (CONFIG.WS_BASE_URL || "").trim();
  if (base.startsWith("ws://")) {
    base = "http://" + base.slice(5);
  } else if (base.startsWith("wss://")) {
    base = "https://" + base.slice(6);
  }
  base = base.replace(/\/+$/, "");
  const endpoint = (CONFIG.WS_ENDPOINT || "/ws-chat").startsWith("/")
    ? CONFIG.WS_ENDPOINT || "/ws-chat"
    : `/${CONFIG.WS_ENDPOINT || "/ws-chat"}`;
  return `${base}${endpoint}`;
};
