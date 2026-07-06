package juribook.audit_service.analytics.service;

import juribook.audit_service.analytics.dto.response.CancellationRateResponse;
import juribook.audit_service.analytics.dto.response.DailyBookingStatsResponse;
import juribook.audit_service.analytics.dto.response.PeakHourResponse;
import juribook.audit_service.analytics.dto.response.SearchedCityResponse;
import juribook.audit_service.analytics.dto.response.SearchedSpecialtyResponse;
import juribook.audit_service.analytics.dto.response.SpecialtyPopularityResponse;
import juribook.audit_service.analytics.entity.DailyBookingStats;
import juribook.audit_service.analytics.entity.PeakHourStats;
import juribook.audit_service.analytics.repository.DailyBookingStatsRepository;
import juribook.audit_service.analytics.repository.PeakHourStatsRepository;
import juribook.audit_service.analytics.repository.SearchedCityStatsRepository;
import juribook.audit_service.analytics.repository.SearchedSpecialtyStatsRepository;
import juribook.audit_service.analytics.repository.SpecialtyPopularityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Lecture des 4 vues matérialisées alimentées par AnalyticsService
 * , jamais de requête sur les tables transactionnelles
 * d'autres services, uniquement ces vues déjà agrégées en temps réel.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final DailyBookingStatsRepository dailyBookingStatsRepository;
    private final SpecialtyPopularityRepository specialtyPopularityRepository;
    private final PeakHourStatsRepository peakHourStatsRepository;
    private final SearchedSpecialtyStatsRepository searchedSpecialtyStatsRepository;
    private final SearchedCityStatsRepository searchedCityStatsRepository;

    @Transactional(readOnly = true)
    public List<DailyBookingStatsResponse> getDailyStats(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

        return dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(effectiveFrom, effectiveTo)
                .stream()
                .map(s -> new DailyBookingStatsResponse(s.getDate(), s.getBookingsCount(), s.getCancellationsCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CancellationRateResponse getCancellationRate(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

        List<DailyBookingStats> stats =
                dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(effectiveFrom, effectiveTo);

        long totalBookings = stats.stream().mapToLong(DailyBookingStats::getBookingsCount).sum();
        long totalCancellations = stats.stream().mapToLong(DailyBookingStats::getCancellationsCount).sum();
        double rate = totalBookings == 0 ? 0.0 : (double) totalCancellations / totalBookings;

        return new CancellationRateResponse(effectiveFrom, effectiveTo, totalBookings, totalCancellations, rate);
    }

    @Transactional(readOnly = true)
    public List<SpecialtyPopularityResponse> getSpecialtyPopularity() {
        return specialtyPopularityRepository.findAllByOrderByBookingsCountDesc()
                .stream()
                .map(s -> new SpecialtyPopularityResponse(s.getSpecialty(), s.getBookingsCount()))
                .toList();
    }

    /** Retourne les 24 heures (0-23), y compris celles à 0 réservation - plus pratique pour un graphique. */
    @Transactional(readOnly = true)
    public List<PeakHourResponse> getPeakHours() {
        Map<Integer, Long> countsByHour = peakHourStatsRepository.findAll().stream()
                .collect(Collectors.toMap(PeakHourStats::getHourOfDay, PeakHourStats::getBookingsCount));

        return IntStream.range(0, 24)
                .mapToObj(hour -> new PeakHourResponse(hour, countsByHour.getOrDefault(hour, 0L)))
                .toList();
    }

    /** Spécialités les plus RECHERCHÉES - distinct de getSpecialtyPopularity (réservations). */
    @Transactional(readOnly = true)
    public List<SearchedSpecialtyResponse> getSearchedSpecialties() {
        return searchedSpecialtyStatsRepository.findAllByOrderBySearchCountDesc()
                .stream()
                .map(s -> new SearchedSpecialtyResponse(s.getSpecialty(), s.getSearchCount()))
                .toList();
    }

    /** Villes les plus recherchées. */
    @Transactional(readOnly = true)
    public List<SearchedCityResponse> getSearchedCities() {
        return searchedCityStatsRepository.findAllByOrderBySearchCountDesc()
                .stream()
                .map(s -> new SearchedCityResponse(s.getCity(), s.getSearchCount()))
                .toList();
    }
}