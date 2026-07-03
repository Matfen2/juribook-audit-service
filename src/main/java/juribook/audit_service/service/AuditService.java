package juribook.audit_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.dto.response.AuditEntryResponse;
import juribook.audit_service.entity.AuditEntry;
import juribook.audit_service.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Ingestion et consultation du journal d'audit.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private static final List<String> ACTOR_FIELD_CANDIDATES =
            List.of("clientId", "lawyerId", "userId", "authUserId", "actorId");

    private final AuditEntryRepository auditEntryRepository;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════
    //  Ingestion
    // ══════════════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════════════
    //  Consultation
    // ══════════════════════════════════════════════════════════
    /**
     * Consultation filtrée, réservée à l'ADMIN (contrôlé au niveau
     * SecurityConfig, pas ici).
     *
     * @param userId filtre optionnel sur actorId - null = tous les acteurs
     * @param from   borne inférieure optionnelle (incluse)
     * @param to     borne supérieure optionnelle (incluse)
     * @param page   numéro de page (0-based)
     * @param size   taille de page (défaut 20, max 50)
     */
    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> search(Long userId, LocalDateTime from, LocalDateTime to,
                                            int page, int size) {
        int safeSize = Math.min(size > 0 ? size : DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, safeSize);

        return auditEntryRepository.search(userId, from, to, pageable)
                .map(AuditEntryResponse::from);
    }
}