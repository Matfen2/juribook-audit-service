package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.abuse.service.AbuseDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingSignalConsumerTest {

    @Mock
    private AbuseDetectionService abuseDetectionService;

    private BookingSignalConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingSignalConsumer(new ObjectMapper().findAndRegisterModules(), abuseDetectionService);
    }

    @Test
    @DisplayName("booking.cancelled - enregistre le signal")
    void onBookingEvent_cancelled_recordsCancellation() {
        String payload = """
            {"eventType":"booking.cancelled","bookingId":7,"clientId":4,"lawyerId":1}
            """;

        consumer.onBookingEvent(payload);

        verify(abuseDetectionService).recordCancellation(4L, 7L);
    }

    @Test
    @DisplayName("booking.created - ignoré, aucun signal enregistré")
    void onBookingEvent_created_ignored() {
        String payload = """
            {"eventType":"booking.created","bookingId":7,"clientId":4,"lawyerId":1}
            """;

        consumer.onBookingEvent(payload);

        verify(abuseDetectionService, never()).recordCancellation(anyLong(), anyLong());
    }

    @Test
    @DisplayName("clientId absent - ignoré, ne plante pas")
    void onBookingEvent_missingClientId_ignoredGracefully() {
        String payload = """
            {"eventType":"booking.cancelled","bookingId":7,"lawyerId":1}
            """;

        assertDoesNotThrow(() -> consumer.onBookingEvent(payload));
        verify(abuseDetectionService, never()).recordCancellation(anyLong(), anyLong());
    }

    @Test
    @DisplayName("JSON malformé — ne plante jamais")
    void onBookingEvent_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onBookingEvent("pas du JSON"));
        verify(abuseDetectionService, never()).recordCancellation(anyLong(), anyLong());
    }
}