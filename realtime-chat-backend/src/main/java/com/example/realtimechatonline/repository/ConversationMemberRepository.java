package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
}
