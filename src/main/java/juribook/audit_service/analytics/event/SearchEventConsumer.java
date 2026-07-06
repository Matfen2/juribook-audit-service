package juribook.audit_service.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consomme search-events pour alimenter les tendances de recherche
 * : spécialités et villes les plus recherchées, compteurs
 * indépendants. Group-id dédié, distinct des autres consumers du
 * package analytics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchEventConsumer {

    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    @KafkaListener(topics = "search-events", groupId = "audit-service-analytics-search-group")
    public void onSearchEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.path("eventType").asText(null);

            if (!"search.performed".equals(eventType)) {
                return;
            }

            String specialty = node.hasNonNull("specialty") ? node.get("specialty").asText(null) : null;
            String city = node.hasNonNull("city") ? node.get("city").asText(null) : null;

            analyticsService.recordSearch(specialty, city);

        } catch (Exception e) {
            log.warn("Payload search-events illisible pour l'analytics, ignoré : {}", e.getMessage());
        }
    }
}