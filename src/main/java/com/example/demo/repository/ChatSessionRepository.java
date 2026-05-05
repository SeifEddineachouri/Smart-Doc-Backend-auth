package com.example.demo.repository;

import com.example.demo.model.entity.ChatSessionEntity;
import com.example.demo.model.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    List<ChatSessionEntity> findAllByUserOrderByUpdatedAtDesc(UserEntity user);

    Optional<ChatSessionEntity> findByIdAndUser(UUID id, UserEntity user);

    Optional<ChatSessionEntity> findByUserAndIsDefaultTrue(UserEntity user);
}
