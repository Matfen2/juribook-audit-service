package juribook.audit_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.dto.response.AuditEntryResponse;
import juribook.audit_service.entity.AuditEntry;
import juribook.audit_service.repository.AuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests d'AuditService : les deux axes du cahier des
 * charges pour ce service : "audit entries validées" (ingestion,
 * extraction best-effort, robustesse sur payload malformé) et la
 * consultation filtrée (Sprint 5.8).
 *
 * ObjectMapper est une vraie instance (pas un mock), le but est de
 * vérifier le comportement réel de désérialisation/extraction, pas de
 * mocker Jackson lui-même.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        auditService = new AuditService(auditEntryRepository, objectMapper);
    }

    // ══════════════════════════════════════════════════════════
    //  Ingestion - extraction best-effort
    // ══════════════════════════════════════════════════════════
    @Test
    void recordEvent_fullPayload_extractsEventTypeActorIdAndOccurredAt() {
        String payload = """
            {"eventType":"booking.created","bookingId":1,"clientId":42,"lawyerId":4,
             "timeSlotId":15,"status":"PENDING","reason":"Litige","occurredAt":"2026-07-01T12:20:43.331"}
            """;

        auditService.recordEvent("booking-events", payload);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("booking-events");
        assertThat(saved.getEventType()).isEqualTo("booking.created");
        // clientId est prioritaire sur lawyerId dans l'ordre de recherche
        assertThat(saved.getActorId()).isEqualTo(42L);
        assertThat(saved.getOccurredAt()).isEqualTo(LocalDateTime.parse("2026-07-01T12:20:43.331"));
        assertThat(saved.getPayload()).isEqualTo(payload);
    }

    @Test
    void recordEvent_actorFieldPriority_lawyerIdUsedWhenClientIdAbsent() {
        String payload = """
            {"eventType":"slot.released","lawyerId":4,"slotId":67}
            """;

        auditService.recordEvent("slot-events", payload);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(4L);
    }

    @Test
    void recordEvent_noRecognizedActorField_actorIdStaysNull() {
        String payload = """
            {"eventType":"document.uploaded","documentId":99}
            """;

        auditService.recordEvent("document-events", payload);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isNull();
        // Le payload brut est quand même sauvegardé intégralement.
        assertThat(captor.getValue().getPayload()).isEqualTo(payload);
    }

    @Test
    void recordEvent_missingEventType_eventTypeStaysNull_stillSaves() {
        String payload = "{\"lawyerId\":4}";

        auditService.recordEvent("lawyer-events", payload);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isNull();
    }

    @Test
    void recordEvent_unparsableOccurredAt_occurredAtStaysNull_stillSaves() {
        String payload = """
            {"eventType":"booking.created","clientId":42,"occurredAt":"pas une date valide"}
            """;

        auditService.recordEvent("booking-events", payload);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getOccurredAt()).isNull();
        assertThat(captor.getValue().getActorId()).isEqualTo(42L); // le reste est quand même extrait
    }

    @Test
    void recordEvent_malformedJson_doesNotThrow_stillSavesRawPayload() {
        String payload = "{ceci n'est pas du JSON valide";

        assertDoesNotThrow(() -> auditService.recordEvent("booking-events", payload));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.getPayload()).isEqualTo(payload);
        assertThat(saved.getEventType()).isNull();
        assertThat(saved.getActorId()).isNull();
    }

    @Test
    void recordEvent_actorFieldPresentButNotNumeric_actorIdStaysNull() {
        // ex: un futur eventType qui utiliserait "clientId" comme chaîne
        // (UUID) plutôt que numérique, ne doit pas planter, juste
        // ignorer ce champ pour l'extraction.
        String payload = """
            {"eventType":"future.event","clientId":"not-a-number"}
            """;

        assertDoesNotThrow(() -> auditService.recordEvent("booking-events", payload));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditEntryRepository).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isNull();
    }

    // ══════════════════════════════════════════════════════════
    //  Consultation filtrée
    // ══════════════════════════════════════════════════════════
    @Test
    void search_delegatesToRepositoryWithGivenFilters_andMapsToResponse() {
        AuditEntry entry = new AuditEntry();
        entry.setId(1L);
        entry.setTopic("booking-events");
        entry.setEventType("booking.created");
        entry.setActorId(42L);
        entry.setPayload("{}");
        entry.setRecordedAt(LocalDateTime.now());

        Page<AuditEntry> page = new PageImpl<>(List.of(entry));
        when(auditEntryRepository.search(eq(42L), any(), any(), any(Pageable.class))).thenReturn(page);

        Page<AuditEntryResponse> result = auditService.search(42L, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).actorId()).isEqualTo(42L);
    }

    @Test
    void search_pageSizeAboveMax_isClampedTo50() {
        when(auditEntryRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditService.search(null, null, null, 0, 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEntryRepository).search(any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}