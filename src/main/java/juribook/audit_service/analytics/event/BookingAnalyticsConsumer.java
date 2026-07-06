package juribook.audit_service.analytics.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Consomme booking-events pour alimenter réservations/jour, taux
 * d'annulation et heures de pointe (Sprint 7.4). Group-id dédié.
 *
 * ⚠️ slotDate/slotStartTime ne sont disponibles que depuis l'enrichissement
 * de BookingEventPublisherImpl (Sprint 7.4, booking-service) — un event
 * publié par une version antérieure du service n'aurait pas ces champs ;
 * gérés comme optionnels ici (hasNonNull) pour rester tolérant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAnalyticsConsumer {

    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;

    @KafkaListener(topics = "booking-events", groupId = "audit-service-analytics-booking-group")
    public void onBookingEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.path("eventType").asText(null);

            if ("booking.created".equals(eventType)) {
                handleBookingCreated(node);
            } else if ("booking.cancelled".equals(eventType)) {
                handleBookingCancelled(node);
            }
            // Autres eventTypes (booking.confirmed, booking.reminder) :
            // hors scope des 4 métriques du 7.4, ignorés délibérément.

        } catch (Exception e) {
            log.warn("Payload booking-events illisible pour l'analytics, ignoré : {}", e.getMessage());
        }
    }

    private void handleBookingCreated(JsonNode node) {
        Long lawyerId = node.hasNonNull("lawyerId") ? node.get("lawyerId").asLong() : null;
        LocalDate day = extractOccurredAtDate(node);
        LocalTime slotStartTime = extractSlotStartTime(node);

        analyticsService.recordBookingCreated(lawyerId, day, slotStartTime);
    }

    private void handleBookingCancelled(JsonNode node) {
        LocalDate day = extractOccurredAtDate(node);
        analyticsService.recordBookingCancelled(day);
    }

    private LocalDate extractOccurredAtDate(JsonNode node) {
        if (!node.hasNonNull("occurredAt")) {
            return null;
        }
        try {
            return LocalDateTime.parse(node.get("occurredAt").asText()).toLocalDate();
        } catch (DateTimeParseException e) {
            log.debug("occurredAt illisible, ignoré : {}", node.get("occurredAt").asText());
            return null;
        }
    }

    private LocalTime extractSlotStartTime(JsonNode node) {
        if (!node.hasNonNull("slotStartTime")) {
            return null;
        }
        try {
            return LocalTime.parse(node.get("slotStartTime").asText());
        } catch (DateTimeParseException e) {
            log.debug("slotStartTime illisible, ignoré : {}", node.get("slotStartTime").asText());
            return null;
        }
    }
}