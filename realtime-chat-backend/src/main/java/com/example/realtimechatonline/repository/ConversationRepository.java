package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.Conversation;
import com.example.realtimechatonline.domain.entity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByTypeAndName(ConversationType type, String name);
}
