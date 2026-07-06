package juribook.audit_service.abuse.event;

import java.time.LocalDateTime;

public record AbuseEvent(
    String eventType,
    Long actorId,
    String reason,
    long signalCount,
    LocalDateTime occurredAt
) {
}