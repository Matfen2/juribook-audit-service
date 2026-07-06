package juribook.audit_service.abuse.event;

public interface AbuseEventPublisher {

    void publishAbuseDetected(Long actorId, String reason, long signalCount);
}