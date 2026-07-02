package juribook.audit_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.entity.AuditEntry;
import juribook.audit_service.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Persistance du journal d'audit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    // Ordre de priorité pour deviner "l'acteur" d'un événement, best
    // effort, pas une garantie. Un booking.created a clientId ET
    // lawyerId : seul le premier trouvé dans cet ordre est retenu.
    // Le payload complet reste la référence en cas d'ambiguïté.
    private static final List<String> ACTOR_FIELD_CANDIDATES =
            List.of("clientId", "lawyerId", "userId", "authUserId", "actorId");

    private final AuditEntryRepository auditEntryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordEvent(String topic, String payload) {
        AuditEntry entry = new AuditEntry();
        entry.setTopic(topic);
        entry.setPayload(payload);

        try {
            JsonNode json = objectMapper.readTree(payload);
            enrichEventType(entry, json);
            enrichActorId(entry, json);
            enrichOccurredAt(entry, json, topic);
        } catch (JsonProcessingException e) {
            // Payload illisible en JSON, tracé quand même tel quel
            // (payload brut), juste sans enrichissement.
            log.warn("Payload non-JSON reçu sur topic={}, tracé brut sans enrichissement", topic);
        }

        auditEntryRepository.save(entry);
        log.info("Événement audité : topic={}, eventType={}, actorId={}",
                entry.getTopic(), entry.getEventType(), entry.getActorId());
    }

    private void enrichEventType(AuditEntry entry, JsonNode json) {
        if (json.hasNonNull("eventType")) {
            entry.setEventType(json.get("eventType").asText());
        }
    }

    private void enrichActorId(AuditEntry entry, JsonNode json) {
        for (String field : ACTOR_FIELD_CANDIDATES) {
            if (json.hasNonNull(field) && json.get(field).isNumber()) {
                entry.setActorId(json.get(field).asLong());
                return;
            }
        }
    }

    private void enrichOccurredAt(AuditEntry entry, JsonNode json, String topic) {
        if (!json.hasNonNull("occurredAt")) {
            return;
        }
        try {
            entry.setOccurredAt(LocalDateTime.parse(json.get("occurredAt").asText()));
        } catch (DateTimeParseException e) {
            log.debug("occurredAt non parsable pour topic={} : {}", topic, json.get("occurredAt"));
        }
    }
}