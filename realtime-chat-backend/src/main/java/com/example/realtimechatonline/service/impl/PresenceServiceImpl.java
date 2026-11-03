package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.dto.response.UserPresenceDto;
import com.example.realtimechatonline.domain.dto.websocket.PresenceEventDto;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;
    private final UserRepository userRepository;

    // username -> Set of active session IDs
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    // sessionId -> username
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();

    // sessionId -> last active timestamp (for TTL / heartbeat expiration check)
    private final ConcurrentHashMap<String, Instant> sessionLastActive = new ConcurrentHashMap<>();

    // username -> lastSeenAt cache
    private final ConcurrentHashMap<String, Instant> userLastSeenAt = new ConcurrentHashMap<>();

    // username -> fine-grained per-user mutex lock
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    private Object getUserLock(String username) {
        return userLocks.computeIfAbsent(username, k -> new Object());
    }

    @Override
    public void addSession(String username, String sessionId) {
        if (username == null || username.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        synchronized (getUserLock(username)) {
            sessionToUser.put(sessionId, username);
            sessionLastActive.put(sessionId, Instant.now());

            Set<String> sessions = userSessions.computeIfAbsent(username,
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            boolean isFirstSession = sessions.isEmpty();
            sessions.add(sessionId);

            if (isFirstSession) {
                log.info("User [{}] is now ONLINE (first session={})", username, sessionId);
                User user = userRepository.findByUsername(username).orElse(null);
                Long userId = user != null ? user.getId() : null;

                PresenceEventDto event = PresenceEventDto.builder()
                        .username(username)
                        .userId(userId)
                        .online(true)
                        .timestamp(Instant.now())
                        .build();

                broadcastPresence(event);
            } else {
                log.debug("User [{}] added concurrent session [{}] (total={})",
                        username, sessionId, sessions.size());
            }
        }
    }

    @Override
    @Transactional
    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String username = sessionToUser.get(sessionId);
        if (username == null) {
            log.debug("Disconnect ignored for untracked session [{}]", sessionId);
            return;
        }

        synchronized (getUserLock(username)) {
            // Re-check inside lock to ensure another thread didn't remove it concurrently
            if (!sessionToUser.containsKey(sessionId)) {
                log.debug("Session [{}] already removed by concurrent thread", sessionId);
                return;
            }

            sessionToUser.remove(sessionId);
            sessionLastActive.remove(sessionId);

            Set<String> sessions = userSessions.get(username);
            boolean isLastSession = false;
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(username);
                    isLastSession = true;
                }
            }

            if (isLastSession) {
                Instant now = Instant.now();
                userLastSeenAt.put(username, now);
                log.info("User [{}] is now OFFLINE (last session [{}] closed)", username, sessionId);

                try {
                    userRepository.updateLastSeenAt(username, now);
                } catch (Exception e) {
                    log.error("Failed to update last_seen_at in DB for user [{}]: {}", username, e.getMessage());
                }

                User user = userRepository.findByUsername(username).orElse(null);
                Long userId = user != null ? user.getId() : null;

                PresenceEventDto event = PresenceEventDto.builder()
                        .username(username)
                        .userId(userId)
                        .online(false)
                        .lastSeenAt(now)
                        .timestamp(now)
                        .build();

                broadcastPresence(event);
            } else {
                int remainingCount = sessions != null ? sessions.size() : 0;
                log.debug("User [{}] closed session [{}] but still has [{}] active session(s)",
                        username, sessionId, remainingCount);
            }
        }
    }

    @Override
    public void touchSession(String sessionId) {
        if (sessionId != null && sessionToUser.containsKey(sessionId)) {
            sessionLastActive.put(sessionId, Instant.now());
        }
    }

    @Override
    public boolean isUserOnline(String username) {
        if (username == null) return false;
        Set<String> sessions = userSessions.get(username);
        return sessions != null && !sessions.isEmpty();
    }

    @Override
    public Set<String> getOnlineUsernames() {
        return new HashSet<>(userSessions.keySet());
    }

    @Override
    public Instant getLastSeenAt(String username) {
        if (username == null) return null;
        Instant cached = userLastSeenAt.get(username);
        if (cached != null) return cached;

        return userRepository.findByUsername(username)
                .map(User::getLastSeenAt)
                .orElse(null);
    }

    @Override
    public List<UserPresenceDto> getPresenceList(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return getAllOnlinePresence();
        }

        List<UserPresenceDto> result = new ArrayList<>();
        for (String uname : usernames) {
            if (uname == null || uname.isBlank()) continue;
            boolean online = isUserOnline(uname);
            Instant lastSeen = online ? null : getLastSeenAt(uname);
            User user = userRepository.findByUsername(uname).orElse(null);
            Long userId = user != null ? user.getId() : null;

            result.add(UserPresenceDto.builder()
                    .userId(userId)
                    .username(uname)
                    .online(online)
                    .lastSeenAt(lastSeen)
                    .build());
        }
        return result;
    }

    @Override
    public List<UserPresenceDto> getAllOnlinePresence() {
        List<UserPresenceDto> result = new ArrayList<>();
        for (String uname : getOnlineUsernames()) {
            User user = userRepository.findByUsername(uname).orElse(null);
            Long userId = user != null ? user.getId() : null;
            result.add(UserPresenceDto.builder()
                    .userId(userId)
                    .username(uname)
                    .online(true)
                    .lastSeenAt(null)
                    .build());
        }
        return result;
    }

    @Scheduled(fixedRate = 30000)
    public void sweepStaleSessions() {
        Instant threshold = Instant.now().minus(Duration.ofSeconds(90));
        List<String> staleSessionIds = sessionLastActive.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(threshold))
                .map(Map.Entry::getKey)
                .toList();

        for (String sid : staleSessionIds) {
            log.warn("Sweeping stale/orphaned WebSocket session: {}", sid);
            removeSession(sid);
        }
    }

    @Override
    public String getUsernameBySession(String sessionId) {
        if (sessionId == null) return null;
        return sessionToUser.get(sessionId);
    }

    private void broadcastPresence(PresenceEventDto event) {
        if (messagingTemplateProvider != null) {
            SimpMessagingTemplate template = messagingTemplateProvider.getIfAvailable();
            if (template != null) {
                template.convertAndSend("/topic/presence", event);
            }
        }
    }
}
