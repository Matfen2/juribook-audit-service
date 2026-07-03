package juribook.audit_service.dto.response;

import juribook.audit_service.entity.AuditEntry;

import java.time.LocalDateTime;

public record AuditEntryResponse(
    Long id,
    String topic,
    String eventType,
    Long actorId,
    String payload,
    LocalDateTime occurredAt,
    LocalDateTime recordedAt
) {
    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.getId(), entry.getTopic(), entry.getEventType(),
                entry.getActorId(), entry.getPayload(),
                entry.getOccurredAt(), entry.getRecordedAt()
        );
    }
}