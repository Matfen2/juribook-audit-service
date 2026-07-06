package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.abuse.service.AbuseDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * ⚠️ groupId dédié ("audit-service-abuse-group"), volontairement
 * DIFFÉRENT de celui d'AuditEventConsumer ("audit-service-group",
 * cf. application.yaml). Si les deux consumers partageaient le même
 * group-id sur ce topic, Kafka répartirait les partitions ENTRE eux
 * (sémantique normale d'un consumer group), ni l'un ni l'autre ne
 * recevrait alors tous les messages. Un group-id distinct donne à
 * chaque consumer group sa propre copie complète et indépendante du
 * flux, exactement ce qu'il faut ici (deux besoins différents sur le
 * même topic : journalisation exhaustive vs détection d'abus filtrée).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingSignalConsumer {

    private final ObjectMapper objectMapper;
    private final AbuseDetectionService abuseDetectionService;

    @KafkaListener(topics = "booking-events", groupId = "audit-service-abuse-group")
    public void onBookingEvent(String payload) {
        AbuseBookingEvent event;
        try {
            event = objectMapper.readValue(payload, AbuseBookingEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Impossible de désérialiser un message du topic booking-events : {}", payload, e);
            return;
        }

        if (!"booking.cancelled".equals(event.eventType()) || event.clientId() == null) {
            return;
        }

        abuseDetectionService.recordCancellation(event.clientId(), event.bookingId());
    }
}