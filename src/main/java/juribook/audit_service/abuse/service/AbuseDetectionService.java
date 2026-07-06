package juribook.audit_service.abuse.service;

import juribook.audit_service.abuse.entity.AbuseAlert;
import juribook.audit_service.abuse.entity.AbuseSignal;
import juribook.audit_service.abuse.entity.SignalType;
import juribook.audit_service.abuse.event.AbuseEventPublisher;
import juribook.audit_service.abuse.repository.AbuseAlertRepository;
import juribook.audit_service.abuse.repository.AbuseSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Détection d'abus par agrégation sur fenêtre glissante.
 *
 * Vit dans audit-service (pas un service dédié) : contrainte du projet
 * limitée à 6 microservices (auth/lawyer/booking/notification/audit/
 * api-gateway). audit-service a déjà toute la plomberie Kafka
 * nécessaire (il écoute déjà booking-events et review-events parmi ses
 * 8 topics), donc aucune nouvelle dépendance requise — seulement du
 * nouveau code, dans son propre sous-package `abuse`, avec ses propres
 * tables et son propre group-id Kafka (cf. BookingSignalConsumer/
 * ReviewSignalConsumer) pour ne pas interférer avec AuditEventConsumer.
 *
 * Approche : chaque signal individuel est persisté (AbuseSignal), et à
 * chaque nouveau signal, un COUNT() SQL sur la fenêtre glissante décide
 * si le seuil est dépassé, même choix de conception que
 * LawyerService.recalculateRating.
 *
 * ⚠️ Limite assumée pour les annulations (recordCancellation) :
 * booking.cancelled est publié dans 4 scénarios différents (refus par
 * l'avocat, annulation client, annulation avocat, désactivation
 * automatique de l'avocat) sans distinguer qui a déclenché
 * l'annulation. Ce service compte donc "les réservations de ce client
 * qui finissent annulées", pas strictement "les annulations initiées
 * par ce client". Le signal avis 1-étoile (recordLowRatingReview) n'a
 * pas cette ambiguïté : review.created porte clientId directement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbuseDetectionService {

    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);
    private static final int CANCELLATION_THRESHOLD = 5;
    private static final String CANCELLATION_REASON = "Plus de 5 annulations en 7 jours";

    private static final Duration LOW_RATING_WINDOW = Duration.ofHours(24);
    private static final int LOW_RATING_THRESHOLD = 3;
    private static final String LOW_RATING_REASON = "Plus de 3 avis 1-étoile en 24h";

    private final AbuseSignalRepository abuseSignalRepository;
    private final AbuseAlertRepository abuseAlertRepository;
    private final AbuseEventPublisher abuseEventPublisher;

    @Transactional
    public void recordCancellation(Long clientId, Long bookingId) {
        recordSignalAndCheckThreshold(
                clientId, SignalType.BOOKING_CANCELLED, bookingId,
                CANCELLATION_WINDOW, CANCELLATION_THRESHOLD, CANCELLATION_REASON
        );
    }

    @Transactional
    public void recordLowRatingReview(Long clientId, Long reviewId) {
        recordSignalAndCheckThreshold(
                clientId, SignalType.LOW_RATING_REVIEW, reviewId,
                LOW_RATING_WINDOW, LOW_RATING_THRESHOLD, LOW_RATING_REASON
        );
    }

    private void recordSignalAndCheckThreshold(Long actorId, SignalType type, Long sourceId,
                                                Duration window, int threshold, String reason) {
        AbuseSignal signal = new AbuseSignal();
        signal.setActorId(actorId);
        signal.setSignalType(type);
        signal.setSourceId(sourceId);
        abuseSignalRepository.save(signal);

        LocalDateTime windowStart = LocalDateTime.now().minus(window);
        long count = abuseSignalRepository.countByActorIdAndSignalTypeAndOccurredAtAfter(actorId, type, windowStart);

        log.debug("Signal enregistré : actorId={}, type={}, sourceId={}, count sur fenêtre={}",
                actorId, type, sourceId, count);

        if (count <= threshold) {
            return;
        }

        boolean alreadyAlerted = abuseAlertRepository.existsByActorIdAndReasonAndTriggeredAtAfter(
                actorId, reason, windowStart);
        if (alreadyAlerted) {
            log.debug("Seuil déjà signalé dans cette fenêtre, pas de nouvelle alerte : actorId={}, reason={}",
                    actorId, reason);
            return;
        }

        AbuseAlert alert = new AbuseAlert();
        alert.setActorId(actorId);
        alert.setReason(reason);
        abuseAlertRepository.save(alert);

        log.warn("Seuil d'abus dépassé : actorId={}, reason={}, count={}", actorId, reason, count);
        abuseEventPublisher.publishAbuseDetected(actorId, reason, count);
    }
}