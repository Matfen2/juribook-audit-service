package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbuseBookingEvent(
    String eventType,
    Long bookingId,
    Long clientId,
    Long lawyerId
) {
}