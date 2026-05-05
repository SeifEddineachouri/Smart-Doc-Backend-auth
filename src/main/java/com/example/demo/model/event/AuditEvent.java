package com.example.demo.model.event;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(
    String eventType,
    String service,
    Instant occurredAt,
    String userId,
    String sessionId,
    String resourceType,
    String resourceId,
    String status,
    Map<String, String> attributes
) {
}

