package juribook.audit_service.analytics.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingAnalyticsConsumer")
class BookingAnalyticsConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    private BookingAnalyticsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BookingAnalyticsConsumer(new ObjectMapper().findAndRegisterModules(), analyticsService);
    }

    @Test
    @DisplayName("booking.created complet - transmet lawyerId, jour et heure du créneau")
    void onBookingEvent_created_fullPayload_passesAllFields() {
        String payload = """
            {"eventType":"booking.created","bookingId":900,"lawyerId":10,
             "occurredAt":"2026-07-05T09:00:00","slotDate":"2026-07-10","slotStartTime":"14:30:00"}
            """;

        consumer.onBookingEvent(payload);

        ArgumentCaptor<LocalDate> dayCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalTime> timeCaptor = ArgumentCaptor.forClass(LocalTime.class);
        verify(analyticsService).recordBookingCreated(eq(10L), dayCaptor.capture(), timeCaptor.capture());

        assertThat(dayCaptor.getValue()).isEqualTo(LocalDate.of(2026, 7, 5)); // jour de l'event, pas du créneau
        assertThat(timeCaptor.getValue()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("booking.created sans slotStartTime - transmet null pour l'heure, ne plante pas")
    void onBookingEvent_created_missingSlotStartTime_passesNull() {
        String payload = """
            {"eventType":"booking.created","bookingId":900,"lawyerId":10,"occurredAt":"2026-07-05T09:00:00"}
            """;

        assertDoesNotThrow(() -> consumer.onBookingEvent(payload));

        verify(analyticsService).recordBookingCreated(eq(10L), eq(LocalDate.of(2026, 7, 5)), isNull());
    }

    @Test
    @DisplayName("booking.cancelled - appelle recordBookingCancelled avec le jour de l'event")
    void onBookingEvent_cancelled_callsRecordCancelled() {
        String payload = """
            {"eventType":"booking.cancelled","bookingId":900,"occurredAt":"2026-07-06T10:00:00"}
            """;

        consumer.onBookingEvent(payload);

        verify(analyticsService).recordBookingCancelled(LocalDate.of(2026, 7, 6));
        verify(analyticsService, never()).recordBookingCreated(any(), any(), any());
    }

    @Test
    @DisplayName("booking.confirmed - hors scope, ignoré")
    void onBookingEvent_confirmed_ignored() {
        String payload = """
            {"eventType":"booking.confirmed","bookingId":900,"occurredAt":"2026-07-05T09:00:00"}
            """;

        consumer.onBookingEvent(payload);

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("JSON malformé - ne plante jamais")
    void onBookingEvent_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onBookingEvent("pas du JSON"));
        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("occurredAt illisible - transmet null pour le jour plutôt que de planter")
    void onBookingEvent_unparsableOccurredAt_passesNullDay() {
        String payload = """
            {"eventType":"booking.created","bookingId":900,"lawyerId":10,"occurredAt":"pas une date"}
            """;

        assertDoesNotThrow(() -> consumer.onBookingEvent(payload));
        verify(analyticsService).recordBookingCreated(eq(10L), isNull(), isNull());
    }
}