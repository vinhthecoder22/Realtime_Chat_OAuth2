package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationMember;
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
import com.example.realtimechatonline.domain.entity.ConversationType;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.repository.ConversationMemberRepository;
import com.example.realtimechatonline.repository.ConversationRepository;
import com.example.realtimechatonline.service.impl.GroupMembershipServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupMembershipServiceImpl Unit Tests")
class GroupMembershipServiceImplTest {

    @Mock
    private ConversationMemberRepository conversationMemberRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private GroupMembershipServiceImpl groupMembershipService;

    private User alice;
    private User bob;
    private Conversation groupConv;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).username("alice").build();
        bob = User.builder().id(2L).username("bob").build();

        groupConv = Conversation.builder()
                .id(10L)
                .type(ConversationType.GROUP)
                .name("Test Group")
                .build();
    }

    @Test
    @DisplayName("getConversationType — queries repository on cache miss and caches result")
    void getConversationType_cachesResult() {
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(groupConv));

        ConversationType type1 = groupMembershipService.getConversationType(10L);
        ConversationType type2 = groupMembershipService.getConversationType(10L);

        assertThat(type1).isEqualTo(ConversationType.GROUP);
        assertThat(type2).isEqualTo(ConversationType.GROUP);
        verify(conversationRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("getConversationType — returns null for invalid ID or not found")
    void getConversationType_invalidOrNotFound() {
        assertThat(groupMembershipService.getConversationType(null)).isNull();
        assertThat(groupMembershipService.getConversationType(0L)).isNull();

        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());
        assertThat(groupMembershipService.getConversationType(999L)).isNull();
    }

    @Test
    @DisplayName("isMemberActive — queries repository on cache miss and identifies active members")
    void isMemberActive_cacheMissAndHit() {
        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenReturn(List.of("alice"));

        boolean aliceActive1 = groupMembershipService.isMemberActive(10L, "alice");
        boolean bobActive1 = groupMembershipService.isMemberActive(10L, "bob");

        assertThat(aliceActive1).isTrue();
        assertThat(bobActive1).isFalse();
        verify(conversationMemberRepository, times(1))
                .findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE);

        // Second check should use memory cache
        boolean aliceActive2 = groupMembershipService.isMemberActive(10L, "alice");
        assertThat(aliceActive2).isTrue();
        verifyNoMoreInteractions(conversationMemberRepository);
    }

    @Test
    @DisplayName("invalidateGroup — purges active members cache so next call reloads DB")
    void invalidateGroup_purgesCache() {
        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenReturn(List.of("alice"));

        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isTrue();

        groupMembershipService.invalidateGroup(10L);

        // Next call re-queries DB
        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isTrue();
        verify(conversationMemberRepository, times(2))
                .findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("removeMember — evicts member and triggers reload on next call")
    void removeMember_evictsMember() {
        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenReturn(List.of("alice"))
                .thenReturn(List.of()); // after removal

        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isTrue();

        groupMembershipService.removeMember(10L, "alice");

        // After removal, subsequent check returns false
        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isFalse();
    }

    @Test
    @DisplayName("addMember — invalidates cache and triggers reload with new member")
    void addMember_invalidatesCache() {
        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenReturn(List.of("alice"))
                .thenReturn(List.of("alice", "bob"));

        assertThat(groupMembershipService.isMemberActive(10L, "bob")).isFalse();

        groupMembershipService.addMember(10L, "bob");

        assertThat(groupMembershipService.isMemberActive(10L, "bob")).isTrue();
    }

    @Test
    @DisplayName("isMemberActive — fail-closed returns false on DB exception or invalid arguments")
    void isMemberActive_handlesExceptionsFailClosed() {
        assertThat(groupMembershipService.isMemberActive(null, "alice")).isFalse();
        assertThat(groupMembershipService.isMemberActive(-1L, "alice")).isFalse();
        assertThat(groupMembershipService.isMemberActive(10L, null)).isFalse();
        assertThat(groupMembershipService.isMemberActive(10L, "")).isFalse();

        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenThrow(new RuntimeException("Database down"));

        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isFalse();
    }

    @Test
    @DisplayName("removeMember — synchronizes cache eviction with active transaction afterCommit")
    void removeMember_synchronizesWithActiveTransaction() {
        when(conversationMemberRepository.findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE))
                .thenReturn(List.of("alice", "bob"))
                .thenReturn(List.of("bob")); // DB state after commit

        // 1. Populate cache
        assertThat(groupMembershipService.isMemberActive(10L, "alice")).isTrue();
        assertThat(groupMembershipService.isMemberActive(10L, "bob")).isTrue();
        verify(conversationMemberRepository, times(1))
                .findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE);

        // 2. Simulate active Spring transaction
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            groupMembershipService.removeMember(10L, "alice");

            // In-memory set removed alice immediately:
            assertThat(groupMembershipService.isMemberActive(10L, "alice")).isFalse();
            assertThat(groupMembershipService.isMemberActive(10L, "bob")).isTrue();

            // Crucial: Cache key has NOT been evicted yet, so no premature DB query while transaction is uncommitted!
            verify(conversationMemberRepository, times(1))
                    .findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE);

            // 3. Simulate Transaction Commit
            for (org.springframework.transaction.support.TransactionSynchronization sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            // 4. After commit, cache is evicted and next call re-queries committed DB state
            assertThat(groupMembershipService.isMemberActive(10L, "bob")).isTrue();
            verify(conversationMemberRepository, times(2))
                    .findActiveUsernamesByConversationIdAndStatus(10L, ConversationMemberStatus.ACTIVE);

        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }
}
