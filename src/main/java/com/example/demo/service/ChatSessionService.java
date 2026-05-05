package com.example.demo.service;

import com.example.demo.model.dto.ChatSessionDto;
import com.example.demo.model.entity.ChatMessageEntity;
import com.example.demo.model.entity.ChatSessionEntity;
import com.example.demo.model.entity.ChatSessionStatus;
import com.example.demo.model.entity.DocumentEntity;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ChatSessionRepository;
import com.example.demo.repository.DocumentRepository;
import com.example.demo.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSessionService {

    private static final String DEFAULT_SESSION_NAME = "Default chat";

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatSessionService(
        ChatSessionRepository chatSessionRepository,
        UserRepository userRepository,
        DocumentRepository documentRepository,
        ChatMessageRepository chatMessageRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ChatSessionEntity resolveSession(UUID userId, UUID sessionId) {
        UserEntity user = getUser(userId);
        if (sessionId == null) {
            return getOrCreateDefaultSession(user);
        }
        return chatSessionRepository.findByIdAndUser(sessionId, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    @Transactional
    public ChatSessionEntity getOrCreateDefaultSession(UserEntity user) {
        return chatSessionRepository.findByUserAndIsDefaultTrue(user)
            .orElseGet(() -> createDefaultSession(user));
    }

    @Transactional
    public ChatSessionDto createSession(UUID userId, String name) {
        UserEntity user = getUser(userId);
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUser(user);
        session.setName((name == null || name.isBlank()) ? "New chat" : name.trim());
        session.setStatus(ChatSessionStatus.ACTIVE);
        session.setDefault(false);
        ChatSessionEntity saved = chatSessionRepository.save(session);
        return toDto(saved, null);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionDto> listSessions(UUID userId) {
        UserEntity user = getUser(userId);
        return chatSessionRepository.findAllByUserOrderByUpdatedAtDesc(user).stream()
            .map(session -> {
                ChatMessageEntity last = chatMessageRepository.findTopByUserAndSessionOrderByCreatedAtDesc(user, session)
                    .orElse(null);
                return toDto(session, last == null ? null : last.getQuestion());
            })
            .toList();
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        UserEntity user = getUser(userId);
        ChatSessionEntity session = chatSessionRepository.findByIdAndUser(sessionId, user)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        List<DocumentEntity> documents = documentRepository.findAllByUserAndSession(user, session);
        for (DocumentEntity document : documents) {
            try {
                Files.deleteIfExists(Path.of(document.getStoragePath()));
            } catch (IOException ignored) {
                // Non-blocking cleanup; metadata deletion already succeeded.
            }
        }
        documentRepository.deleteAll(documents);
        chatMessageRepository.deleteAllByUserAndSession(user, session);
        chatSessionRepository.delete(session);
    }

    @Transactional
    public void touchSession(ChatSessionEntity session) {
        session.setUpdatedAt(Instant.now());
        chatSessionRepository.save(session);
    }

    private ChatSessionEntity createDefaultSession(UserEntity user) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUser(user);
        session.setName(DEFAULT_SESSION_NAME);
        session.setStatus(ChatSessionStatus.ACTIVE);
        session.setDefault(true);
        ChatSessionEntity saved = chatSessionRepository.save(session);

        documentRepository.assignDefaultSession(user, saved);
        chatMessageRepository.assignDefaultSession(user, saved);

        return saved;
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
    }

    private ChatSessionDto toDto(ChatSessionEntity session, String lastQuestion) {
        return new ChatSessionDto(
            session.getId(),
            session.getName(),
            session.getCreatedAt(),
            session.getUpdatedAt(),
            lastQuestion
        );
    }
}

