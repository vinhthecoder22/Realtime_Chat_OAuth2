package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.ConversationMember;
import com.example.realtimechatonline.domain.entity.ConversationMemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByConversationId(Long conversationId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationMember> findByConversationIdAndStatus(Long conversationId, ConversationMemberStatus status);

    boolean existsByConversationIdAndUserIdAndStatus(Long conversationId, Long userId, ConversationMemberStatus status);

    Optional<ConversationMember> findByConversationIdAndUserIdAndStatus(Long conversationId, Long userId, ConversationMemberStatus status);

    long countByConversationIdAndStatus(Long conversationId, ConversationMemberStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<ConversationMember> findByConversationIdIn(List<Long> conversationIds);

    @EntityGraph(attributePaths = {"user"})
    List<ConversationMember> findByConversationIdInAndStatus(List<Long> conversationIds, ConversationMemberStatus status);

    @Modifying
    @Query("UPDATE ConversationMember cm SET cm.lastReadMessageId = :targetReadId, cm.lastReadAt = :readAt " +
            "WHERE cm.id = :memberId AND (cm.lastReadMessageId IS NULL OR cm.lastReadMessageId < :targetReadId)")
    int updateLastReadIfGreater(@Param("memberId") Long memberId,
                                @Param("targetReadId") Long targetReadId,
                                @Param("readAt") Instant readAt);

    List<ConversationMember> findByUserIdAndConversationIdIn(Long userId, List<Long> conversationIds);

    @Query("SELECT cm.conversation.id, COUNT(cm) FROM ConversationMember cm WHERE cm.conversation.id IN :conversationIds AND cm.status = :status GROUP BY cm.conversation.id")
    List<Object[]> countMembersByConversationIdInAndStatus(@Param("conversationIds") List<Long> conversationIds, @Param("status") ConversationMemberStatus status);

    @Query("SELECT cm FROM ConversationMember cm JOIN FETCH cm.user WHERE cm.conversation.id = :conversationId AND cm.status = :status ORDER BY cm.role ASC, cm.joinedAt ASC")
    List<ConversationMember> findActiveMembersWithUser(@Param("conversationId") Long conversationId, @Param("status") ConversationMemberStatus status);

    @Query("SELECT cm FROM ConversationMember cm WHERE cm.conversation.id = :conversationId AND cm.user.id != :userId AND cm.status = :status ORDER BY cm.joinedAt ASC, cm.id ASC")
    List<ConversationMember> findOtherActiveMembersOrderedByJoinedAt(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("status") ConversationMemberStatus status);

}
