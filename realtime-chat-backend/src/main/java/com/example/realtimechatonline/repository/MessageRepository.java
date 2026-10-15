package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<Message> findAllByOrderByTimestampAsc();

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findAllByOrderByTimestampDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByTimestampDesc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.id IN (" +
            "  SELECT MAX(m2.id) FROM Message m2 WHERE m2.conversation.id IN :conversationIds GROUP BY m2.conversation.id" +
            ")")
    List<Message> findLatestMessagesByConversationIds(@Param("conversationIds") List<Long> conversationIds);

    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.id = (" +
            "  SELECT MAX(m2.id) FROM Message m2 WHERE m2.conversation.id = :conversationId" +
            ")")
    Optional<Message> findLatestMessageByConversationId(@Param("conversationId") Long conversationId);

    @Query("SELECT m.conversation.id, COUNT(m.id) " +
            "FROM Message m " +
            "JOIN m.conversation c " +
            "LEFT JOIN ConversationMember cm ON cm.conversation.id = c.id AND cm.user.id = :userId " +
            "WHERE m.conversation.id IN :conversationIds " +
            "  AND m.sender.id <> :userId " +
            "  AND ( " +
            "       (c.type = com.example.realtimechatonline.domain.entity.ConversationType.DIRECT AND (cm.id IS NULL OR cm.status = com.example.realtimechatonline.domain.entity.ConversationMemberStatus.ACTIVE) AND (cm.lastReadMessageId IS NULL OR m.id > cm.lastReadMessageId)) " +
            "    OR (c.type = com.example.realtimechatonline.domain.entity.ConversationType.GROUP AND cm.id IS NOT NULL AND cm.status = com.example.realtimechatonline.domain.entity.ConversationMemberStatus.ACTIVE AND (cm.lastReadMessageId IS NULL OR m.id > cm.lastReadMessageId)) " +
            "    OR (c.type = com.example.realtimechatonline.domain.entity.ConversationType.PUBLIC AND cm.id IS NOT NULL AND cm.lastReadMessageId IS NOT NULL AND m.id > cm.lastReadMessageId) " +
            "  ) " +
            "GROUP BY m.conversation.id")
    List<Object[]> countUnreadMessagesByConversationIds(@Param("conversationIds") List<Long> conversationIds, @Param("userId") Long userId);

    @Query("SELECT COUNT(m.id) " +
            "FROM Message m " +
            "JOIN m.conversation c " +
            "LEFT JOIN ConversationMember cm ON cm.conversation.id = c.id AND cm.user.id = :userId " +
            "WHERE m.conversation.id = :conversationId " +
            "  AND m.sender.id <> :userId " +
            "  AND ( " +
            "       (c.type = com.example.realtimechatonline.domain.entity.ConversationType.DIRECT AND (cm.id IS NULL OR cm.status = com.example.realtimechatonline.domain.entity.ConversationMemberStatus.ACTIVE) AND (cm.lastReadMessageId IS NULL OR m.id > cm.lastReadMessageId)) " +
            "    OR (c.type = com.example.realtimechatonline.domain.entity.ConversationType.GROUP AND cm.id IS NOT NULL AND cm.status = com.example.realtimechatonline.domain.entity.ConversationMemberStatus.ACTIVE AND (cm.lastReadMessageId IS NULL OR m.id > cm.lastReadMessageId)) " +
            "    OR (c.type = com.example.realtimechatonline.domain.entity.ConversationType.PUBLIC AND cm.id IS NOT NULL AND cm.lastReadMessageId IS NOT NULL AND m.id > cm.lastReadMessageId) " +
            "  )")
    long countUnreadMessages(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}