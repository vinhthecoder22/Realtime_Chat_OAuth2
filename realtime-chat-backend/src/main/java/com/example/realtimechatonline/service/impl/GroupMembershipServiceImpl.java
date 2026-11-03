package com.example.realtimechatonline.service.impl;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationMember;
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.service.GroupMembershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMembershipServiceImpl implements GroupMembershipService {

    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;

    private final ConcurrentHashMap<Long, ConversationType> conversationTypeCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Set<String>> activeMembersCache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public ConversationType getConversationType(Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            return null;
        }
        return conversationTypeCache.computeIfAbsent(conversationId, id ->
                conversationRepository.findById(id).map(Conversation::getType).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMemberActive(Long conversationId, String username) {
        if (conversationId == null || conversationId <= 0 || username == null || username.isBlank()) {
            return false;
        }

        try {
            Set<String> activeMembers = activeMembersCache.get(conversationId);
            if (activeMembers == null) {
                List<String> usernames = conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(
                        conversationId, ConversationMemberStatus.ACTIVE);

                Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
                if (usernames != null) {
                    set.addAll(usernames);
                }
                activeMembersCache.put(conversationId, set);
                activeMembers = set;
            }

            return activeMembers.contains(username);
        } catch (Exception ex) {
            log.error("Error verifying active membership for user [{}] in conversation [{}]: {}",
                    username, conversationId, ex.getMessage());
            return false;
        }
    }

    @Override
    public void invalidateGroup(Long conversationId) {
        if (conversationId != null) {
            evictCacheAfterCommit(conversationId);
            log.debug("Active members cache eviction scheduled for conversation {}", conversationId);
        }
    }

    @Override
    public void removeMember(Long conversationId, String username) {
        if (conversationId != null && username != null) {
            Set<String> set = activeMembersCache.get(conversationId);
            if (set != null) {
                set.remove(username);
            }
            evictCacheAfterCommit(conversationId);
            log.debug("User [{}] removed from conversation [{}] active cache", username, conversationId);
        }
    }

    @Override
    public void addMember(Long conversationId, String username) {
        if (conversationId != null && username != null) {
            Set<String> set = activeMembersCache.get(conversationId);
            if (set != null) {
                set.add(username);
            }
            evictCacheAfterCommit(conversationId);
            log.debug("User [{}] added to conversation [{}] active cache", username, conversationId);
        }
    }

    private void evictCacheAfterCommit(Long conversationId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            activeMembersCache.remove(conversationId);
                            log.debug("Active members cache evicted after commit for conversation {}", conversationId);
                        }
                    }
            );
        } else {
            activeMembersCache.remove(conversationId);
            log.debug("Active members cache evicted immediately (non-transactional) for conversation {}", conversationId);
        }
    }
}
