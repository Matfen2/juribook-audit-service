package juribook.audit_service.event;

import juribook.audit_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumer Kafka unique, abonné aux 8 topics de la plateforme (Sprint 5.7).
 *
 * Un seul listener plutôt que 8 dédiés : l'audit n'a pas besoin de
 * connaître la structure de chaque type d'événement pour les tracer —
 * il capture le topic + le payload brut, et délègue l'extraction
 * best-effort à AuditService. Ce choix rend aussi ce service
 * "future-proof" : quand lawyer-events, review-events, search-events,
 * document-events et abuse-events auront de vrais producteurs (sprints
 * à venir), aucune modification n'est nécessaire ici — ils sont déjà
 * dans la liste des topics écoutés depuis le Sprint 5.1
 * (provisionnement centralisé, cf. juribook-docker/kafka-init).
 *
 * @Header(KafkaHeaders.RECEIVED_TOPIC) permet de savoir de quel topic
 * vient chaque message reçu, puisqu'un seul listener couvre les 8.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditService auditService;

    @KafkaListener(
        topics = {
            "booking-events", "slot-events", "lawyer-events", "review-events",
            "audit-events", "search-events", "document-events", "abuse-events"
        },
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        log.debug("Événement reçu sur topic={}", topic);
        auditService.recordEvent(topic, payload);
    }
}