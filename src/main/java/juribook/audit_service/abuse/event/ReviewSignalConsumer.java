package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.abuse.service.AbuseDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewSignalConsumer {

    private final ObjectMapper objectMapper;
    private final AbuseDetectionService abuseDetectionService;

    @KafkaListener(topics = "review-events", groupId = "audit-service-abuse-group")
    public void onReviewEvent(String payload) {
        AbuseReviewEvent event;
        try {
            event = objectMapper.readValue(payload, AbuseReviewEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Impossible de désérialiser un message du topic review-events : {}", payload, e);
            return;
        }

        if (!"review.created".equals(event.eventType()) || event.rating() != 1 || event.clientId() == null) {
            return;
        }

        abuseDetectionService.recordLowRatingReview(event.clientId(), event.reviewId());
    }
}