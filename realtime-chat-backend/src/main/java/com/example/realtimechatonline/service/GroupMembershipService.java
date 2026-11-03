package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.entity.ConversationType;

public interface GroupMembershipService {

    /**
     * Retrieves the ConversationType for a given conversationId (cached).
     */
    ConversationType getConversationType(Long conversationId);

    /**
     * Checks if a user is currently an ACTIVE member of a conversation.
     * Uses in-memory cache to avoid N+1 queries on outbound message broadcast.
     * Fails closed (returns false) if error occurs or user is not active.
     */
    boolean isMemberActive(Long conversationId, String username);

    /**
     * Invalidates the active members cache for a given conversation.
     * Called whenever members are added, removed, or leave.
     */
    void invalidateGroup(Long conversationId);

    /**
     * Surgically removes a user from active member cache for a conversation.
     */
    void removeMember(Long conversationId, String username);

    /**
     * Surgically adds a user to active member cache for a conversation.
     */
    void addMember(Long conversationId, String username);

}