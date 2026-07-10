package juribook.audit_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.dto.response.AuditEntryResponse;
import juribook.audit_service.entity.AuditEntry;
import juribook.audit_service.repository.AuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de AuditService.getBookingHistory, fichier
 * séparé de l'AuditServiceTest existant (jamais vu en entier), pour ne
 * pas risquer de dupliquer/écraser ses mocks ou son setup à l'aveugle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService.getBookingHistory (Sprint 7.7)")
class AuditServiceBookingHistoryTest {

    @Mock
    private AuditEntryRepository auditEntryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEntryRepository, objectMapper);
    }

    private AuditEntry buildEntry(Long id, String topic, String eventType, LocalDateTime occurredAt) {
        AuditEntry entry = new AuditEntry();
        entry.setId(id);
        entry.setTopic(topic);
        entry.setEventType(eventType);
        entry.setActorId(42L);
        entry.setPayload("{\"eventType\":\"" + eventType + "\",\"bookingId\":900}");
        entry.setOccurredAt(occurredAt);
        entry.setRecordedAt(occurredAt);
        return entry;
    }

    @Test
    @DisplayName("délègue au repository et mappe chaque entrée en AuditEntryResponse")
    void getBookingHistory_delegatesAndMaps() {
        AuditEntry created = buildEntry(1L, "booking-events", "booking.created", LocalDateTime.of(2026, 7, 1, 9, 0));
        AuditEntry confirmed = buildEntry(2L, "booking-events", "booking.confirmed", LocalDateTime.of(2026, 7, 1, 10, 0));
        when(auditEntryRepository.findByBookingId(900L)).thenReturn(List.of(created, confirmed));

        List<AuditEntryResponse> result = auditService.getBookingHistory(900L);

        assertThat(result).hasSize(2);
        verify(auditEntryRepository).findByBookingId(900L);
    }

    @Test
    @DisplayName("aucun événement trouvé - retourne une liste vide, ne plante pas")
    void getBookingHistory_noEvents_returnsEmptyList() {
        when(auditEntryRepository.findByBookingId(999L)).thenReturn(List.of());

        List<AuditEntryResponse> result = auditService.getBookingHistory(999L);

        assertThat(result).isEmpty();
    }
}