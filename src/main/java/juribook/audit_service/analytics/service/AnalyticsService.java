package juribook.audit_service.analytics.service;

import juribook.audit_service.analytics.entity.DailyBookingStats;
import juribook.audit_service.analytics.entity.LawyerSpecialtyCache;
import juribook.audit_service.analytics.entity.PeakHourStats;
import juribook.audit_service.analytics.entity.SearchedCityStats;
import juribook.audit_service.analytics.entity.SearchedSpecialtyStats;
import juribook.audit_service.analytics.entity.SpecialtyPopularity;
import juribook.audit_service.analytics.repository.DailyBookingStatsRepository;
import juribook.audit_service.analytics.repository.LawyerSpecialtyCacheRepository;
import juribook.audit_service.analytics.repository.PeakHourStatsRepository;
import juribook.audit_service.analytics.repository.SearchedCityStatsRepository;
import juribook.audit_service.analytics.repository.SearchedSpecialtyStatsRepository;
import juribook.audit_service.analytics.repository.SpecialtyPopularityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Incrémente les 4 vues matérialisées à partir des events
 * consommés par LawyerSpecialtyCacheConsumer et BookingAnalyticsConsumer.
 *
 * Pattern lecture-puis-écriture (pas de requête SQL atomique de type
 * UPSERT/increment), même choix de conception que LawyerService.
 * recalculateRating et AbuseDetectionService, accepté ailleurs dans le
 * projet, mais vaut la peine de noter la même limite théorique de
 * concurrence (deux événements quasi simultanés pour la même clé
 * pourraient se marcher dessus sans verrou pessimiste). Le débit Kafka
 * réel de cette plateforme ne justifie pas cette complexité pour l'instant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final LawyerSpecialtyCacheRepository lawyerSpecialtyCacheRepository;
    private final DailyBookingStatsRepository dailyBookingStatsRepository;
    private final SpecialtyPopularityRepository specialtyPopularityRepository;
    private final PeakHourStatsRepository peakHourStatsRepository;
    private final SearchedSpecialtyStatsRepository searchedSpecialtyStatsRepository;
    private final SearchedCityStatsRepository searchedCityStatsRepository;

    @Transactional
    public void upsertLawyerSpecialty(Long lawyerId, String specialty) {
        if (lawyerId == null || specialty == null || specialty.isBlank()) {
            log.debug("upsertLawyerSpecialty ignoré (donnée manquante) : lawyerId={}, specialty={}", lawyerId, specialty);
            return;
        }

        LawyerSpecialtyCache cache = lawyerSpecialtyCacheRepository.findById(lawyerId)
                .orElseGet(() -> {
                    LawyerSpecialtyCache c = new LawyerSpecialtyCache();
                    c.setLawyerId(lawyerId);
                    return c;
                });
        cache.setSpecialty(specialty);
        lawyerSpecialtyCacheRepository.save(cache);

        log.debug("Cache spécialité mis à jour : lawyerId={}, specialty={}", lawyerId, specialty);
    }

    /**
     * booking.created : incrémente réservations/jour (jour de l'event),
     * heures de pointe (heure du CRÉNEAU, si connue) et spécialités
     * populaires (via le cache lawyerId→specialty, si connu).
     */
    @Transactional
    public void recordBookingCreated(Long lawyerId, LocalDate day, LocalTime slotStartTime) {
        if (day == null) {
            log.debug("recordBookingCreated ignoré (pas de date d'événement)");
            return;
        }

        incrementDailyBookings(day);

        if (slotStartTime != null) {
            incrementPeakHour(slotStartTime.getHour());
        }

        if (lawyerId != null) {
            lawyerSpecialtyCacheRepository.findById(lawyerId).ifPresentOrElse(
                    cache -> incrementSpecialtyPopularity(cache.getSpecialty()),
                    () -> log.debug("Spécialité inconnue pour lawyerId={} — specialty-popularity non incrémenté", lawyerId)
            );
        }
    }

    /** booking.cancelled : incrémente le compteur d'annulations du jour de l'événement. */
    @Transactional
    public void recordBookingCancelled(LocalDate day) {
        if (day == null) {
            log.debug("recordBookingCancelled ignoré (pas de date d'événement)");
            return;
        }

        DailyBookingStats stats = getOrCreateDailyStats(day);
        stats.setCancellationsCount(stats.getCancellationsCount() + 1);
        dailyBookingStatsRepository.save(stats);
    }

    /**
     * search.performed : incrémente indépendamment le
     * compteur de la spécialité recherchée et celui de la ville
     * recherchée. Une recherche sans filtre (specialty/city null) ne
     * fait rien pour la dimension absente, sans planter.
     */
    @Transactional
    public void recordSearch(String specialty, String city) {
        if (specialty != null && !specialty.isBlank()) {
            incrementSearchedSpecialty(specialty);
        }
        if (city != null && !city.isBlank()) {
            incrementSearchedCity(city);
        }
    }

    // ── Helpers privés ────────────────────────────────────────
    private void incrementDailyBookings(LocalDate day) {
        DailyBookingStats stats = getOrCreateDailyStats(day);
        stats.setBookingsCount(stats.getBookingsCount() + 1);
        dailyBookingStatsRepository.save(stats);
    }

    private DailyBookingStats getOrCreateDailyStats(LocalDate day) {
        return dailyBookingStatsRepository.findById(day).orElseGet(() -> {
            DailyBookingStats s = new DailyBookingStats();
            s.setDate(day);
            return s;
        });
    }

    private void incrementPeakHour(int hour) {
        PeakHourStats stats = peakHourStatsRepository.findById(hour).orElseGet(() -> {
            PeakHourStats s = new PeakHourStats();
            s.setHourOfDay(hour);
            return s;
        });
        stats.setBookingsCount(stats.getBookingsCount() + 1);
        peakHourStatsRepository.save(stats);
    }

    private void incrementSpecialtyPopularity(String specialty) {
        SpecialtyPopularity pop = specialtyPopularityRepository.findById(specialty).orElseGet(() -> {
            SpecialtyPopularity p = new SpecialtyPopularity();
            p.setSpecialty(specialty);
            return p;
        });
        pop.setBookingsCount(pop.getBookingsCount() + 1);
        specialtyPopularityRepository.save(pop);
    }

    private void incrementSearchedSpecialty(String specialty) {
        SearchedSpecialtyStats stats = searchedSpecialtyStatsRepository.findById(specialty).orElseGet(() -> {
            SearchedSpecialtyStats s = new SearchedSpecialtyStats();
            s.setSpecialty(specialty);
            return s;
        });
        stats.setSearchCount(stats.getSearchCount() + 1);
        searchedSpecialtyStatsRepository.save(stats);
    }

    private void incrementSearchedCity(String city) {
        SearchedCityStats stats = searchedCityStatsRepository.findById(city).orElseGet(() -> {
            SearchedCityStats s = new SearchedCityStats();
            s.setCity(city);
            return s;
        });
        stats.setSearchCount(stats.getSearchCount() + 1);
        searchedCityStatsRepository.save(stats);
    }
}