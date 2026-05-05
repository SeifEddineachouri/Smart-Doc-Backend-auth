package com.example.demo.repository;

import com.example.demo.model.entity.ChatMessageEntity;
import com.example.demo.model.entity.ChatSessionEntity;
import com.example.demo.model.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Page<ChatMessageEntity> findAllByUserOrderByCreatedAtDesc(UserEntity user, Pageable pageable);

    Page<ChatMessageEntity> findAllByUserAndSessionOrderByCreatedAtDesc(UserEntity user, ChatSessionEntity session, Pageable pageable);

    Optional<ChatMessageEntity> findTopByUserAndSessionOrderByCreatedAtDesc(UserEntity user, ChatSessionEntity session);

    @Modifying
    @Query("update ChatMessageEntity m set m.session = :session where m.user = :user and m.session is null")
    int assignDefaultSession(@Param("user") UserEntity user, @Param("session") ChatSessionEntity session);

    void deleteAllByUserAndSession(UserEntity user, ChatSessionEntity session);
}

