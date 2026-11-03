package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.UserPresenceDto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PresenceService {

    void addSession(String username, String sessionId);
    
    void removeSession(String sessionId);

    void touchSession(String sessionId);

    boolean isUserOnline(String username);

    Set<String> getOnlineUsernames();

    Instant getLastSeenAt(String username);

    List<UserPresenceDto> getPresenceList(Collection<String> usernames);

    List<UserPresenceDto> getAllOnlinePresence();

    String getUsernameBySession(String sessionId);

}
