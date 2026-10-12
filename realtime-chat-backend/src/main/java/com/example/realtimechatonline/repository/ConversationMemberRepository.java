package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.ConversationMember;
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

    @EntityGraph(attributePaths = {"user"})
    List<ConversationMember> findByConversationIdIn(List<Long> conversationIds);

    @Modifying
    @Query("UPDATE ConversationMember cm SET cm.lastReadMessageId = :targetReadId, cm.lastReadAt = :readAt " +
            "WHERE cm.id = :memberId AND (cm.lastReadMessageId IS NULL OR cm.lastReadMessageId < :targetReadId)")
    int updateLastReadIfGreater(@Param("memberId") Long memberId,
                                @Param("targetReadId") Long targetReadId,
                                @Param("readAt") Instant readAt);

}
