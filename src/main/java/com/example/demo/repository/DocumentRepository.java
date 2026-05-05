package com.example.demo.repository;

import com.example.demo.model.entity.ChatSessionEntity;
import com.example.demo.model.entity.DocumentEntity;
import com.example.demo.model.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findAllByUserOrderByCreatedAtDesc(UserEntity user);

    List<DocumentEntity> findAllByUserAndSessionOrderByCreatedAtDesc(UserEntity user, ChatSessionEntity session);

    List<DocumentEntity> findAllByUserAndSession(UserEntity user, ChatSessionEntity session);

    List<DocumentEntity> findAllByUserAndSessionAndIdIn(UserEntity user, ChatSessionEntity session, List<Long> ids);

    Optional<DocumentEntity> findByIdAndUser(Long id, UserEntity user);

    Optional<DocumentEntity> findByIdAndUserAndSession(Long id, UserEntity user, ChatSessionEntity session);

    @Modifying
    @Query("update DocumentEntity d set d.session = :session where d.user = :user and d.session is null")
    int assignDefaultSession(@Param("user") UserEntity user, @Param("session") ChatSessionEntity session);
}


