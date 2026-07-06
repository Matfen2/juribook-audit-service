package juribook.audit_service.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consomme lawyer-events pour alimenter le cache lawyerId→specialty
 * . Group-id dédié, distinct d'AuditEventConsumer et des
 * consumers du package abuse, pour ne pas interférer avec eux.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LawyerSpecialtyCacheConsumer {

    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    @KafkaListener(topics = "lawyer-events", groupId = "audit-service-analytics-specialty-group")
    public void onLawyerEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.path("eventType").asText(null);

            if (!"lawyer.approved".equals(eventType)) {
                return;
            }

            if (!node.hasNonNull("lawyerId") || !node.hasNonNull("specialty")) {
                log.debug("lawyer.approved sans lawyerId/specialty exploitable, ignoré");
                return;
            }

            Long lawyerId = node.get("lawyerId").asLong();
            String specialty = node.get("specialty").asText(null);

            analyticsService.upsertLawyerSpecialty(lawyerId, specialty);

        } catch (Exception e) {
            log.warn("Payload lawyer-events illisible pour l'analytics, ignoré : {}", e.getMessage());
        }
    }
}