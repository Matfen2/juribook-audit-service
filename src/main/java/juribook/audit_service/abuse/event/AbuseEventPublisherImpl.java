package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Même pattern ObjectProvider<KafkaTemplate> que tous les autres
 * publishers du projet, jamais @ConditionalOnBean.
 *
 * ⚠️ audit-service ne PRODUISAIT jamais rien sur Kafka jusqu'ici (pur
 * consumer, cf. son README), c'est le premier producer de ce service.
 * Vérifie que spring-boot-starter-kafka couvre bien aussi le rôle
 * producer (c'est le cas, la même dépendance sert les deux sens), pas
 * de nouvelle dépendance à ajouter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbuseEventPublisherImpl implements AbuseEventPublisher {

    private static final String TOPIC = "abuse-events";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void publishAbuseDetected(Long actorId, String reason, long signalCount) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();

        if (kafkaTemplate == null) {
            log.debug("Kafka désactivé - événement abuse.detected non publié pour actorId={}", actorId);
            return;
        }

        AbuseEvent event = new AbuseEvent("abuse.detected", actorId, reason, signalCount, LocalDateTime.now());

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, actorId.toString(), payload);
            log.info("Événement Kafka publié : type=abuse.detected, actorId={}, reason={}, signalCount={}, topic={}",
                    actorId, reason, signalCount, TOPIC);
        } catch (JsonProcessingException e) {
            log.error("Échec de sérialisation de l'événement abuse.detected pour actorId={}", actorId, e);
        }
    }
}