package com.example.demo.service;

import com.example.demo.config.AuditKafkaProperties;
import com.example.demo.model.event.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuditKafkaProperties properties;
    private final ObjectMapper objectMapper;

    public AuditEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate,
        AuditKafkaProperties properties,
        ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void documentUploaded(UUID userId, Long documentId, UUID sessionId, String mimeType, long sizeBytes) {
        publish(
            "document.uploaded",
            userId,
            sessionId,
            "document",
            String.valueOf(documentId),
            "success",
            Map.of(
                "mimeType", safeValue(mimeType),
                "sizeBytes", String.valueOf(sizeBytes)
            )
        );
    }

    public void documentDeleted(UUID userId, Long documentId, UUID sessionId) {
        publish(
            "document.deleted",
            userId,
            sessionId,
            "document",
            String.valueOf(documentId),
            "success",
            Map.of()
        );
    }

    public void aiQuestionRequested(UUID userId, UUID sessionId, int documentCount, int questionChars) {
        publish(
            "ai.question.requested",
            userId,
            sessionId,
            "ai-question",
            sessionId == null ? null : sessionId.toString(),
            "success",
            Map.of(
                "documentCount", String.valueOf(documentCount),
                "questionChars", String.valueOf(questionChars)
            )
        );
    }

    public void aiQuestionCompleted(UUID userId, UUID sessionId, int documentCount, int answerChars) {
        publish(
            "ai.question.completed",
            userId,
            sessionId,
            "ai-question",
            sessionId == null ? null : sessionId.toString(),
            "success",
            Map.of(
                "documentCount", String.valueOf(documentCount),
                "answerChars", String.valueOf(answerChars)
            )
        );
    }

    public void aiQuestionFailed(UUID userId, UUID sessionId, int documentCount, String status, String reason) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("documentCount", String.valueOf(documentCount));
        attributes.put("reason", safeValue(reason));
        publish(
            "ai.question.failed",
            userId,
            sessionId,
            "ai-question",
            sessionId == null ? null : sessionId.toString(),
            status,
            attributes
        );
    }

    private void publish(
        String eventType,
        UUID userId,
        UUID sessionId,
        String resourceType,
        String resourceId,
        String status,
        Map<String, String> attributes
    ) {
        try {
            AuditEvent event = new AuditEvent(
                eventType,
                "smartdoc",
                Instant.now(),
                userId == null ? null : userId.toString(),
                sessionId == null ? null : sessionId.toString(),
                resourceType,
                resourceId,
                status,
                attributes
            );
            String payload = objectMapper.writeValueAsString(event);
            String key = buildKey(eventType, userId, resourceId);
            CompletableFuture<?> future = kafkaTemplate.send(properties.getAuditTopic(), key, payload);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    LOGGER.warn("Kafka audit publish failed eventType={} userId={} resourceId={}", eventType, userId, resourceId, ex);
                }
            });
        } catch (JsonProcessingException ex) {
            LOGGER.warn("Kafka audit serialization failed eventType={} userId={} resourceId={}", eventType, userId, resourceId, ex);
        } catch (RuntimeException ex) {
            LOGGER.warn("Kafka audit publish skipped eventType={} userId={} resourceId={}", eventType, userId, resourceId, ex);
        }
    }

    private String buildKey(String eventType, UUID userId, String resourceId) {
        return safeValue(eventType) + ":" + safeValue(userId == null ? null : userId.toString()) + ":" + safeValue(resourceId);
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}


