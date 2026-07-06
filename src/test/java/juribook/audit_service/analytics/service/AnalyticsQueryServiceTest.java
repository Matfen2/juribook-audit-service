package juribook.audit_service.analytics.service;

import juribook.audit_service.analytics.dto.response.CancellationRateResponse;
import juribook.audit_service.analytics.dto.response.DailyBookingStatsResponse;
import juribook.audit_service.analytics.dto.response.PeakHourResponse;
import juribook.audit_service.analytics.dto.response.SearchedCityResponse;
import juribook.audit_service.analytics.dto.response.SearchedSpecialtyResponse;
import juribook.audit_service.analytics.dto.response.SpecialtyPopularityResponse;
import juribook.audit_service.analytics.entity.DailyBookingStats;
import juribook.audit_service.analytics.entity.PeakHourStats;
import juribook.audit_service.analytics.entity.SearchedCityStats;
import juribook.audit_service.analytics.entity.SearchedSpecialtyStats;
import juribook.audit_service.analytics.entity.SpecialtyPopularity;
import juribook.audit_service.analytics.repository.DailyBookingStatsRepository;
import juribook.audit_service.analytics.repository.PeakHourStatsRepository;
import juribook.audit_service.analytics.repository.SearchedCityStatsRepository;
import juribook.audit_service.analytics.repository.SearchedSpecialtyStatsRepository;
import juribook.audit_service.analytics.repository.SpecialtyPopularityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsQueryService")
class AnalyticsQueryServiceTest {

    @Mock private DailyBookingStatsRepository dailyBookingStatsRepository;
    @Mock private SpecialtyPopularityRepository specialtyPopularityRepository;
    @Mock private PeakHourStatsRepository peakHourStatsRepository;
    @Mock private SearchedSpecialtyStatsRepository searchedSpecialtyStatsRepository;
    @Mock private SearchedCityStatsRepository searchedCityStatsRepository;

    @InjectMocks
    private AnalyticsQueryService analyticsQueryService;

    private DailyBookingStats buildDay(LocalDate date, long bookings, long cancellations) {
        DailyBookingStats s = new DailyBookingStats();
        s.setDate(date);
        s.setBookingsCount(bookings);
        s.setCancellationsCount(cancellations);
        return s;
    }

    @Nested
    @DisplayName("getDailyStats")
    class GetDailyStats {

        @Test
        @DisplayName("plage explicite - délègue telle quelle au repository, mappe en DTO")
        void getDailyStats_explicitRange_delegatesAndMaps() {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 5);
            when(dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(from, to))
                    .thenReturn(List.of(buildDay(from, 3, 1)));

            List<DailyBookingStatsResponse> result = analyticsQueryService.getDailyStats(from, to);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).bookingsCount()).isEqualTo(3);
            assertThat(result.get(0).cancellationsCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("plage absente - retombe sur les 30 derniers jours par défaut")
        void getDailyStats_noRange_defaultsToLast30Days() {
            when(dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                    .thenReturn(List.of());

            analyticsQueryService.getDailyStats(null, null);

            var captor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
            org.mockito.Mockito.verify(dailyBookingStatsRepository)
                    .findByDateBetweenOrderByDateAsc(captor.capture(), captor.capture());

            LocalDate from = captor.getAllValues().get(0);
            LocalDate to = captor.getAllValues().get(1);
            assertThat(to).isEqualTo(LocalDate.now());
            assertThat(from).isEqualTo(LocalDate.now().minusDays(29));
        }
    }

    @Nested
    @DisplayName("getCancellationRate")
    class GetCancellationRate {

        @Test
        @DisplayName("agrège correctement sur plusieurs jours")
        void getCancellationRate_aggregatesAcrossDays() {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 2);
            when(dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(from, to)).thenReturn(List.of(
                    buildDay(from, 10, 2),
                    buildDay(to, 10, 3)
            ));

            CancellationRateResponse response = analyticsQueryService.getCancellationRate(from, to);

            assertThat(response.totalBookings()).isEqualTo(20);
            assertThat(response.totalCancellations()).isEqualTo(5);
            assertThat(response.cancellationRate()).isCloseTo(0.25, within(0.0001));
        }

        @Test
        @DisplayName("aucune réservation sur la période - taux à 0.0, pas de division par zéro")
        void getCancellationRate_noBookings_rateIsZero() {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 2);
            when(dailyBookingStatsRepository.findByDateBetweenOrderByDateAsc(from, to)).thenReturn(List.of());

            CancellationRateResponse response = analyticsQueryService.getCancellationRate(from, to);

            assertThat(response.totalBookings()).isZero();
            assertThat(response.cancellationRate()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getSpecialtyPopularity")
    class GetSpecialtyPopularity {

        @Test
        @DisplayName("délègue au repository trié par popularité décroissante")
        void getSpecialtyPopularity_delegatesSorted() {
            SpecialtyPopularity top = new SpecialtyPopularity();
            top.setSpecialty("Droit du travail");
            top.setBookingsCount(50);
            when(specialtyPopularityRepository.findAllByOrderByBookingsCountDesc()).thenReturn(List.of(top));

            List<SpecialtyPopularityResponse> result = analyticsQueryService.getSpecialtyPopularity();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).specialty()).isEqualTo("Droit du travail");
        }
    }

    @Nested
    @DisplayName("getPeakHours")
    class GetPeakHours {

        @Test
        @DisplayName("retourne les 24 heures, comble les heures sans donnée avec 0")
        void getPeakHours_returnsAll24Hours_fillsGapsWithZero() {
            PeakHourStats hour14 = new PeakHourStats();
            hour14.setHourOfDay(14);
            hour14.setBookingsCount(7);
            when(peakHourStatsRepository.findAll()).thenReturn(List.of(hour14));

            List<PeakHourResponse> result = analyticsQueryService.getPeakHours();

            assertThat(result).hasSize(24);
            assertThat(result.get(14).bookingsCount()).isEqualTo(7);
            assertThat(result.get(0).bookingsCount()).isZero();
            assertThat(result.get(23).bookingsCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getSearchedSpecialties")
    class GetSearchedSpecialties {

        @Test
        @DisplayName("délègue au repository trié par nombre de recherches décroissant")
        void getSearchedSpecialties_delegatesSorted() {
            SearchedSpecialtyStats top = new SearchedSpecialtyStats();
            top.setSpecialty("Droit du travail");
            top.setSearchCount(120);
            when(searchedSpecialtyStatsRepository.findAllByOrderBySearchCountDesc()).thenReturn(List.of(top));

            List<SearchedSpecialtyResponse> result = analyticsQueryService.getSearchedSpecialties();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).specialty()).isEqualTo("Droit du travail");
            assertThat(result.get(0).searchCount()).isEqualTo(120);
        }
    }

    @Nested
    @DisplayName("getSearchedCities")
    class GetSearchedCities {

        @Test
        @DisplayName("délègue au repository trié par nombre de recherches décroissant")
        void getSearchedCities_delegatesSorted() {
            SearchedCityStats top = new SearchedCityStats();
            top.setCity("Paris");
            top.setSearchCount(80);
            when(searchedCityStatsRepository.findAllByOrderBySearchCountDesc()).thenReturn(List.of(top));

            List<SearchedCityResponse> result = analyticsQueryService.getSearchedCities();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).city()).isEqualTo("Paris");
            assertThat(result.get(0).searchCount()).isEqualTo(80);
        }
    }
}