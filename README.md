# Realtime Chat Platform

A full-stack real-time messaging platform built with **Spring Boot 3** (REST API + WebSocket/STOMP) and **React 19 / Vite**. It supports a global public room, private 1-on-1 chats between friends, and role-based group conversations with live membership management.

## Key Features

- **Authentication** — Register/login with JWT, Google OAuth2
- **Chat** — Public room, direct messages, group chats, typing indicators, read receipts
- **Groups** — Admin roles, add/remove members, transfer admin, leave group
- **Friends** — Friend requests (send, accept, reject) and friend list
- **Presence** — Online/offline status across multiple tabs and devices
- **UI** — Dark/light theme, mobile-responsive layout

## Tech Stack

| Category | Technologies |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security 6, JJWT, OAuth2 Client |
| Realtime | Spring WebSocket, STOMP, SockJS |
| Database | MySQL 8, Spring Data JPA, Hibernate |
| Frontend | React 19, Vite, React Router 7, `@stomp/stompjs` |
| API docs | SpringDoc OpenAPI (Swagger UI) |

## Engineering Highlights

- **STOMP CONNECT authentication** — Browsers can't send custom headers on the WebSocket handshake, so the JWT is sent in the STOMP `CONNECT` frame and verified by a channel interceptor. Invalid or expired tokens end the session immediately.
- **Subscription authorization** — Subscribing to `/topic/conversation.{id}` is checked against conversation membership (direct participant or `ACTIVE` group member).
- **Outbound frame revocation** — A kicked or banned user stops receiving messages instantly: an outbound interceptor drops frames for users who are no longer active members, without disconnecting them.
- **Multi-session presence** — Tracks every session per user; `OFFLINE` is broadcast only when the last session closes, and a scheduled job cleans up stale sessions.
- **Resilient reconnects** — The client creates a fresh SockJS instance on every reconnect and guards against React StrictMode double-mounting.

## Getting Started

### Prerequisites

- JDK 17+
- Node.js 18+ (20 LTS recommended)
- MySQL 8+

### 1. Clone

```bash
git clone <repo-url>
cd ChatRealtime
```

### 2. Create the database

```sql
CREATE DATABASE IF NOT EXISTS realtime_chat
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

### 3. Configure

**Backend** — edit `realtime-chat-backend/src/main/resources/application-dev.properties`:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Backend port |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/realtime_chat` | MySQL URL |
| `spring.datasource.username` / `password` | `root` / `root` | Database credentials |
| `jwt.secret` | — | HMAC secret for signing JWTs (use a long random value) |
| `jwt.expiration-ms` | `86400000` | Token lifetime (24 h) |
| `app.oauth2.redirect-uri` | `http://localhost:5173/oauth2-redirect` | Frontend redirect after Google login |
| `app.cors.allowed-origins` | `http://localhost:5173` | Allowed origins for REST and WebSocket |

**Frontend** — `realtime-chat-frontend/.env.development`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=http://localhost:8080
```

### 4. Run

```bash
# Backend (http://localhost:8080)
cd realtime-chat-backend
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run

# Frontend (http://localhost:5173) — in a new terminal
cd realtime-chat-frontend
npm install
npm run dev
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## API Overview

REST base path: `/api/v1`

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /oauth2/authorization/google` |
| Conversations | `GET /conversations`, `POST /conversations/direct`, `POST /conversations/group`, `PATCH /conversations/{id}`, `POST /conversations/{id}/leave`, `POST /conversations/{id}/read` |
| Group members | `GET\|POST /conversations/{id}/members`, `DELETE /conversations/{id}/members/{userId}`, `PATCH /conversations/{id}/admin` |
| Messages | `GET /chat/history/page`, `GET /chat/{conversationId}/messages` |
| Users | `GET /users/search?q=`, `GET /users/presence` |
| Friends | `POST /friend-requests`, `GET /friend-requests/received`, `PATCH /friend-requests/{id}/accept\|reject`, `GET /friends` |

### WebSocket (STOMP)

Connect via SockJS at `/ws-chat` and send `Authorization: Bearer <jwt>` in the `CONNECT` frame.

| Destination | Direction | Purpose |
|---|---|---|
| `/app/chat.sendMessage` | Client → Server | Send a message |
| `/app/chat.typing` | Client → Server | Typing indicator |
| `/app/chat.addUser` | Client → Server | Join notification in public room |
| `/topic/public` | Server → Client | Public room stream |
| `/topic/conversation.{id}` | Server → Client | Direct/group messages, typing, read receipts |
| `/topic/presence` | Server → Client | Online/offline updates |
| `/user/queue/events` | Server → Client | Conversation updates, membership eviction |
| `/user/queue/errors` | Server → Client | Errors and permission rejections |

## Project Structure

```text
ChatRealtime/
├── realtime-chat-backend/        # Spring Boot application
│   └── src/main/java/com/example/realtimechatonline/
│       ├── config/               # WebSocket, Security, CORS, OpenAPI
│       ├── controller/           # REST + STOMP controllers
│       ├── domain/               # Entities, DTOs, enums
│       ├── repository/           # Spring Data JPA
│       ├── security/             # JWT, filters, channel interceptors
│       └── service/              # Business logic, presence, membership
└── realtime-chat-frontend/       # React application
    └── src/
        ├── components/           # Auth forms, chat UI, modals
        ├── contexts/             # AuthContext
        ├── hooks/                # useChat, useDebounce, useTheme
        └── pages/                # Chat, Login, OAuth2 redirect
```

## Testing

```bash
# Backend: security, interceptors, presence
cd realtime-chat-backend
./mvnw test

# Frontend: production build check
cd realtime-chat-frontend
npm run build
```

## License

No license specified yet. Add a `LICENSE` file before distributing this project.