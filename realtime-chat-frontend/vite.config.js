import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws-chat": {
        target: "http://localhost:8080",
        ws: true,
        changeOrigin: true,
        // Suppress noisy ECONNABORTED errors when backend is down
        configure: (proxy) => {
          proxy.on("error", (err, _req, res) => {
            if (err.code === "ECONNABORTED" || err.code === "ECONNREFUSED" || err.code === "ECONNRESET") return;
            console.error("[ws-chat proxy]", err.message);
          });
          proxy.on("proxyReqWs", (_proxyReq, _req, socket) => {
            socket.on("error", (err) => {
              if (err.code === "ECONNABORTED" || err.code === "ECONNRESET") return;
            });
          });
        },
      },
      "/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/login/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  preview: {
    port: 5173,
    host: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ws-chat": {
        target: "http://localhost:8080",
        ws: true,
        changeOrigin: true,
        // Suppress noisy ECONNABORTED errors when backend is down
        configure: (proxy) => {
          proxy.on("error", (err, _req, res) => {
            if (err.code === "ECONNABORTED" || err.code === "ECONNREFUSED" || err.code === "ECONNRESET") return;
            console.error("[ws-chat proxy]", err.message);
          });
          proxy.on("proxyReqWs", (_proxyReq, _req, socket) => {
            socket.on("error", (err) => {
              if (err.code === "ECONNABORTED" || err.code === "ECONNRESET") return;
            });
          });
        },
      },
      "/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/login/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
