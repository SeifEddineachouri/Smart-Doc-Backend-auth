package com.example.demo.repository;

import com.example.demo.model.entity.RefreshTokenEntity;
import com.example.demo.model.entity.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshTokenEntity> findByUserAndRevokedFalse(UserEntity user);

    void deleteByExpiresAtBefore(Instant instant);
}


