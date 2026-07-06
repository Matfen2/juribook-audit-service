package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbuseReviewEvent(
    String eventType,
    Long reviewId,
    Long clientId,
    int rating
) {
}