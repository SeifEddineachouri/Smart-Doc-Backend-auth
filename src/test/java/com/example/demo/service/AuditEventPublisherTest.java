package com.example.demo.service;

import com.example.demo.config.AuditKafkaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> topicCaptor;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> payloadCaptor;

    @Test
    void publishesRedactedAuditEventToKafka() {
        AuditKafkaProperties properties = new AuditKafkaProperties();
        properties.setBootstrapServers("localhost:9092");
        properties.setAuditTopic("smartdoc.audit.events");
        properties.setClientId("smartdoc");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AuditEventPublisher publisher = new AuditEventPublisher(kafkaTemplate, properties, objectMapper);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.documentUploaded(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), 42L, UUID.fromString("123e4567-e89b-12d3-a456-426614174111"), "application/pdf", 2048);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        assertEquals("smartdoc.audit.events", topicCaptor.getValue());
        assertTrue(keyCaptor.getValue().contains("document.uploaded"));
        assertTrue(payloadCaptor.getValue().contains("\"eventType\":\"document.uploaded\""));
        assertTrue(payloadCaptor.getValue().contains("\"resourceType\":\"document\""));
        assertTrue(payloadCaptor.getValue().contains("\"mimeType\":\"application/pdf\""));
        assertTrue(payloadCaptor.getValue().contains("\"sizeBytes\":\"2048\""));
    }
}


