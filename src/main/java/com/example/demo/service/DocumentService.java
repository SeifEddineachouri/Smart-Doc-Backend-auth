package com.example.demo.service;

import com.example.demo.config.DocumentProperties;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.dto.UploadDocumentResponseDto;
import com.example.demo.model.dto.UploadedDocumentDto;
import com.example.demo.model.entity.DocumentEntity;
import com.example.demo.model.entity.UserEntity;
import com.example.demo.repository.DocumentRepository;
import com.example.demo.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AiDocumentIngestionService aiDocumentIngestionService;
    private final ChatSessionService chatSessionService;
    private final AuditEventPublisher auditEventPublisher;
    private final Path baseDir;

    public DocumentService(
        DocumentRepository documentRepository,
        UserRepository userRepository,
        AiDocumentIngestionService aiDocumentIngestionService,
        ChatSessionService chatSessionService,
        AuditEventPublisher auditEventPublisher,
        DocumentProperties properties
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.aiDocumentIngestionService = aiDocumentIngestionService;
        this.chatSessionService = chatSessionService;
        this.auditEventPublisher = auditEventPublisher;
        this.baseDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public UploadDocumentResponseDto upload(UUID userId, MultipartFile file) {
        return upload(userId, file, null);
    }

    @Transactional
    public UploadDocumentResponseDto upload(UUID userId, MultipartFile file, UUID sessionId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
        var session = chatSessionService.resolveSession(userId, sessionId);

        String originalName = file.getOriginalFilename() == null ? "document.bin" : file.getOriginalFilename();
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();

        String storageName = UUID.randomUUID() + "-" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path userDir = baseDir.resolve(userId.toString());
        Path target = userDir.resolve(storageName);

        try {
            Files.createDirectories(userDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setUser(user);
        entity.setSession(session);
        entity.setOriginalName(originalName);
        entity.setMimeType(mimeType);
        entity.setSizeBytes(file.getSize());
        entity.setStoragePath(target.toString());

        DocumentEntity saved = documentRepository.save(entity);

        // Best-effort ingestion: upload remains successful even if AI gateway is unavailable.
        aiDocumentIngestionService.ingestOnUpload(userId, saved.getId(), file, mimeType, originalName);
        chatSessionService.touchSession(session);
        auditEventPublisher.documentUploaded(userId, saved.getId(), session.getId(), mimeType, saved.getSizeBytes());

        return new UploadDocumentResponseDto(
            saved.getId(),
            saved.getOriginalName(),
            formatBytes(saved.getSizeBytes()),
            saved.getMimeType(),
            session.getId()
        );
    }

    @Transactional(readOnly = true)
    public List<UploadedDocumentDto> list(UUID userId) {
        return list(userId, null);
    }

    @Transactional(readOnly = true)
    public List<UploadedDocumentDto> list(UUID userId, UUID sessionId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
        var session = chatSessionService.resolveSession(userId, sessionId);

        return documentRepository.findAllByUserAndSessionOrderByCreatedAtDesc(user, session).stream()
            .map(doc -> new UploadedDocumentDto(
                doc.getId(),
                doc.getOriginalName(),
                formatBytes(doc.getSizeBytes()),
                doc.getMimeType(),
                session.getId()
            ))
            .toList();
    }

    @Transactional
    public void delete(UUID userId, Long documentId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));

        DocumentEntity document = documentRepository.findByIdAndUser(documentId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        documentRepository.delete(document);
        try {
            Files.deleteIfExists(Path.of(document.getStoragePath()));
        } catch (IOException ignored) {
            // Non-blocking cleanup; metadata deletion already succeeded.
        }
        auditEventPublisher.documentDeleted(userId, documentId, document.getSession().getId());
    }

    private String formatBytes(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.ROOT, "%.1f MB", mb);
    }
}


