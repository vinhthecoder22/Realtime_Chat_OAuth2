package com.example.realtimechatonline.repository;

import com.example.realtimechatonline.domain.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<Message> findAllByOrderByTimestampAsc();

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findAllByOrderByTimestampDesc(Pageable pageable);

}