package juribook.audit_service.event;

import juribook.audit_service.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Tests du consumer audit : un seul listener couvre les 8
 * topics de la plateforme, ce test vérifie qu'il délègue systématiquement
 * à AuditService avec le bon topic et le bon payload, quel que soit le
 * topic d'origine.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditEventConsumer consumer;

    @Test
    void onEvent_bookingEventsTopic_delegatesToAuditServiceWithCorrectTopic() {
        String payload = """
            {"eventType":"booking.created","bookingId":1,"clientId":2,"lawyerId":3}
            """;

        consumer.onEvent(payload, "booking-events");

        verify(auditService).recordEvent("booking-events", payload);
    }

    @Test
    void onEvent_lawyerEventsTopic_delegatesToAuditServiceWithCorrectTopic() {
        String payload = """
            {"eventType":"lawyer.status-changed","lawyerId":4,"available":false}
            """;

        consumer.onEvent(payload, "lawyer-events");

        verify(auditService).recordEvent("lawyer-events", payload);
    }

    @Test
    void onEvent_unrecognizedPayloadShape_stillDelegates_letsAuditServiceHandleIt() {
        // Le consumer ne parse rien lui-même, même un payload
        // totalement inattendu (topic pas encore utilisé par un vrai
        // producteur, ex: search-events) est transmis tel quel.
        String payload = "{\"whatever\":\"shape\"}";

        consumer.onEvent(payload, "search-events");

        verify(auditService).recordEvent("search-events", payload);
    }
}