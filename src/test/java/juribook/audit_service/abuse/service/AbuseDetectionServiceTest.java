package juribook.audit_service.abuse.service;

import juribook.audit_service.abuse.entity.AbuseAlert;
import juribook.audit_service.abuse.entity.SignalType;
import juribook.audit_service.abuse.event.AbuseEventPublisher;
import juribook.audit_service.abuse.repository.AbuseAlertRepository;
import juribook.audit_service.abuse.repository.AbuseSignalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests d'AbuseDetectionService, le cœur du mécanisme :
 * comptage sur fenêtre glissante, franchissement de seuil, et surtout
 * le dédoublonnage (ne jamais republier abuse.detected à chaque
 * nouveau signal une fois le seuil déjà franchi).
 */
@ExtendWith(MockitoExtension.class)
class AbuseDetectionServiceTest {

    @Mock private AbuseSignalRepository abuseSignalRepository;
    @Mock private AbuseAlertRepository abuseAlertRepository;
    @Mock private AbuseEventPublisher abuseEventPublisher;

    @InjectMocks
    private AbuseDetectionService abuseDetectionService;

    private static final Long ACTOR_ID = 4L;
    private static final Long BOOKING_ID = 7L;
    private static final Long REVIEW_ID = 12L;

    @Test
    @DisplayName("annulations - sous le seuil (5), aucune alerte publiée")
    void recordCancellation_underThreshold_noAlert() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(
                eq(ACTOR_ID), eq(SignalType.BOOKING_CANCELLED), any())).thenReturn(3L);

        abuseDetectionService.recordCancellation(ACTOR_ID, BOOKING_ID);

        verifyNoInteractions(abuseEventPublisher);
        verify(abuseAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("annulations - seuil dépassé (6 > 5), alerte publiée une fois")
    void recordCancellation_overThreshold_publishesAlert() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(
                eq(ACTOR_ID), eq(SignalType.BOOKING_CANCELLED), any())).thenReturn(6L);
        when(abuseAlertRepository.existsByActorIdAndReasonAndTriggeredAtAfter(eq(ACTOR_ID), any(), any()))
                .thenReturn(false);

        abuseDetectionService.recordCancellation(ACTOR_ID, BOOKING_ID);

        verify(abuseAlertRepository).save(any(AbuseAlert.class));
        verify(abuseEventPublisher).publishAbuseDetected(eq(ACTOR_ID), contains("annulations"), eq(6L));
    }

    @Test
    @DisplayName("annulations - seuil déjà dépassé ET déjà alerté dans la fenêtre : pas de republication")
    void recordCancellation_overThresholdButAlreadyAlerted_doesNotRepublish() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(
                eq(ACTOR_ID), eq(SignalType.BOOKING_CANCELLED), any())).thenReturn(8L);
        when(abuseAlertRepository.existsByActorIdAndReasonAndTriggeredAtAfter(eq(ACTOR_ID), any(), any()))
                .thenReturn(true);

        abuseDetectionService.recordCancellation(ACTOR_ID, BOOKING_ID);

        verify(abuseAlertRepository, never()).save(any());
        verifyNoInteractions(abuseEventPublisher);
    }

    @Test
    @DisplayName("annulations - exactement au seuil (5, pas plus) : pas d'alerte, condition stricte '> 5'")
    void recordCancellation_exactlyAtThreshold_noAlert() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(
                eq(ACTOR_ID), eq(SignalType.BOOKING_CANCELLED), any())).thenReturn(5L);

        abuseDetectionService.recordCancellation(ACTOR_ID, BOOKING_ID);

        verifyNoInteractions(abuseEventPublisher);
    }

    @Test
    @DisplayName("avis 1-étoile - seuil dépassé (4 > 3), alerte publiée avec le bon motif")
    void recordLowRatingReview_overThreshold_publishesAlertWithCorrectReason() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(
                eq(ACTOR_ID), eq(SignalType.LOW_RATING_REVIEW), any())).thenReturn(4L);
        when(abuseAlertRepository.existsByActorIdAndReasonAndTriggeredAtAfter(eq(ACTOR_ID), any(), any()))
                .thenReturn(false);

        abuseDetectionService.recordLowRatingReview(ACTOR_ID, REVIEW_ID);

        verify(abuseEventPublisher).publishAbuseDetected(eq(ACTOR_ID), contains("1-étoile"), eq(4L));
    }

    @Test
    @DisplayName("le signal est toujours persisté, même sous le seuil")
    void recordCancellation_alwaysSavesSignalRegardlessOfThreshold() {
        when(abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(any(), any(), any()))
                .thenReturn(1L);

        abuseDetectionService.recordCancellation(ACTOR_ID, BOOKING_ID);

        verify(abuseSignalRepository).save(argThat(signal ->
                signal.getActorId().equals(ACTOR_ID)
                && signal.getSignalType() == SignalType.BOOKING_CANCELLED
                && signal.getSourceId().equals(BOOKING_ID)
        ));
    }
}