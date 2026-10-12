package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByTypeAndName(ConversationType type, String name);

    @Query("SELECT c FROM Conversation c JOIN ConversationMember cm ON c.id = cm.conversation.id WHERE cm.user.id = :userId")
    List<Conversation> findAllByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT c FROM Conversation c " +
            "LEFT JOIN Message m ON m.conversation.id = c.id " +
            "WHERE c.type = com.example.realtimechatonline.domain.entity.ConversationType.PUBLIC " +
            "   OR c.id IN (SELECT cm.conversation.id FROM ConversationMember cm WHERE cm.user.id = :userId) " +
            "GROUP BY c.id, c.type, c.name, c.createdAt " +
            "ORDER BY COALESCE(MAX(m.timestamp), c.createdAt) DESC, c.id DESC",
            countQuery = "SELECT COUNT(c) FROM Conversation c " +
                    "WHERE c.type = com.example.realtimechatonline.domain.entity.ConversationType.PUBLIC " +
                    "   OR c.id IN (SELECT cm.conversation.id FROM ConversationMember cm WHERE cm.user.id = :userId)")
    Page<Conversation> findUserConversationsOrderByLastActivity(@Param("userId") Long userId, Pageable pageable);
}
