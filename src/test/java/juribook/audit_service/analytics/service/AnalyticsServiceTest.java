package juribook.audit_service.analytics.service;

import juribook.audit_service.analytics.entity.DailyBookingStats;
import juribook.audit_service.analytics.entity.LawyerSpecialtyCache;
import juribook.audit_service.analytics.entity.PeakHourStats;
import juribook.audit_service.analytics.entity.SearchedSpecialtyStats;
import juribook.audit_service.analytics.entity.SpecialtyPopularity;
import juribook.audit_service.analytics.repository.DailyBookingStatsRepository;
import juribook.audit_service.analytics.repository.LawyerSpecialtyCacheRepository;
import juribook.audit_service.analytics.repository.PeakHourStatsRepository;
import juribook.audit_service.analytics.repository.SearchedCityStatsRepository;
import juribook.audit_service.analytics.repository.SearchedSpecialtyStatsRepository;
import juribook.audit_service.analytics.repository.SpecialtyPopularityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService")
class AnalyticsServiceTest {

    @Mock private LawyerSpecialtyCacheRepository lawyerSpecialtyCacheRepository;
    @Mock private DailyBookingStatsRepository dailyBookingStatsRepository;
    @Mock private SpecialtyPopularityRepository specialtyPopularityRepository;
    @Mock private PeakHourStatsRepository peakHourStatsRepository;
    @Mock private SearchedSpecialtyStatsRepository searchedSpecialtyStatsRepository;
    @Mock private SearchedCityStatsRepository searchedCityStatsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private static final Long LAWYER_ID = 10L;
    private static final LocalDate DAY = LocalDate.of(2026, 7, 5);

    @Nested
    @DisplayName("upsertLawyerSpecialty")
    class UpsertLawyerSpecialty {

        @Test
        @DisplayName("nouvelle entrée - crée le cache avec la spécialité")
        void upsertLawyerSpecialty_newEntry_createsCache() {
            when(lawyerSpecialtyCacheRepository.findById(LAWYER_ID)).thenReturn(Optional.empty());
            when(lawyerSpecialtyCacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.upsertLawyerSpecialty(LAWYER_ID, "Droit du travail");

            ArgumentCaptor<LawyerSpecialtyCache> captor = ArgumentCaptor.forClass(LawyerSpecialtyCache.class);
            verify(lawyerSpecialtyCacheRepository).save(captor.capture());
            assertThat(captor.getValue().getLawyerId()).isEqualTo(LAWYER_ID);
            assertThat(captor.getValue().getSpecialty()).isEqualTo("Droit du travail");
        }

        @Test
        @DisplayName("entrée existante - écrase l'ancienne spécialité (changement de spécialité déclaré)")
        void upsertLawyerSpecialty_existingEntry_overwritesSpecialty() {
            LawyerSpecialtyCache existing = new LawyerSpecialtyCache();
            existing.setLawyerId(LAWYER_ID);
            existing.setSpecialty("Droit pénal");
            when(lawyerSpecialtyCacheRepository.findById(LAWYER_ID)).thenReturn(Optional.of(existing));
            when(lawyerSpecialtyCacheRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.upsertLawyerSpecialty(LAWYER_ID, "Droit du travail");

            assertThat(existing.getSpecialty()).isEqualTo("Droit du travail");
        }

        @Test
        @DisplayName("lawyerId ou specialty null - ignoré, aucune écriture")
        void upsertLawyerSpecialty_nullArgs_noWrite() {
            analyticsService.upsertLawyerSpecialty(null, "Droit du travail");
            analyticsService.upsertLawyerSpecialty(LAWYER_ID, null);
            analyticsService.upsertLawyerSpecialty(LAWYER_ID, "  ");

            verifyNoInteractions(lawyerSpecialtyCacheRepository);
        }
    }

    @Nested
    @DisplayName("recordBookingCreated")
    class RecordBookingCreated {

        @Test
        @DisplayName("incrémente réservations/jour")
        void recordBookingCreated_incrementsDailyBookings() {
            when(dailyBookingStatsRepository.findById(DAY)).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCreated(null, DAY, null);

            ArgumentCaptor<DailyBookingStats> captor = ArgumentCaptor.forClass(DailyBookingStats.class);
            verify(dailyBookingStatsRepository).save(captor.capture());
            assertThat(captor.getValue().getDate()).isEqualTo(DAY);
            assertThat(captor.getValue().getBookingsCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("jour déjà existant - incrémente le compteur existant, pas de remplacement à 1")
        void recordBookingCreated_existingDay_incrementsExistingCounter() {
            DailyBookingStats existing = new DailyBookingStats();
            existing.setDate(DAY);
            existing.setBookingsCount(4L);
            when(dailyBookingStatsRepository.findById(DAY)).thenReturn(Optional.of(existing));
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCreated(null, DAY, null);

            assertThat(existing.getBookingsCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("slotStartTime fourni - incrémente heures de pointe sur la bonne heure")
        void recordBookingCreated_withSlotStartTime_incrementsPeakHour() {
            when(dailyBookingStatsRepository.findById(any())).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(peakHourStatsRepository.findById(14)).thenReturn(Optional.empty());
            when(peakHourStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCreated(null, DAY, LocalTime.of(14, 30));

            ArgumentCaptor<PeakHourStats> captor = ArgumentCaptor.forClass(PeakHourStats.class);
            verify(peakHourStatsRepository).save(captor.capture());
            assertThat(captor.getValue().getHourOfDay()).isEqualTo(14);
            assertThat(captor.getValue().getBookingsCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("slotStartTime absent - n'incrémente pas heures de pointe")
        void recordBookingCreated_noSlotStartTime_skipsPeakHour() {
            when(dailyBookingStatsRepository.findById(any())).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCreated(null, DAY, null);

            verifyNoInteractions(peakHourStatsRepository);
        }

        @Test
        @DisplayName("lawyerId connu dans le cache - incrémente specialty-popularity pour la bonne spécialité")
        void recordBookingCreated_knownLawyer_incrementsSpecialtyPopularity() {
            when(dailyBookingStatsRepository.findById(any())).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LawyerSpecialtyCache cache = new LawyerSpecialtyCache();
            cache.setLawyerId(LAWYER_ID);
            cache.setSpecialty("Droit du travail");
            when(lawyerSpecialtyCacheRepository.findById(LAWYER_ID)).thenReturn(Optional.of(cache));
            when(specialtyPopularityRepository.findById("Droit du travail")).thenReturn(Optional.empty());
            when(specialtyPopularityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCreated(LAWYER_ID, DAY, null);

            ArgumentCaptor<SpecialtyPopularity> captor = ArgumentCaptor.forClass(SpecialtyPopularity.class);
            verify(specialtyPopularityRepository).save(captor.capture());
            assertThat(captor.getValue().getSpecialty()).isEqualTo("Droit du travail");
            assertThat(captor.getValue().getBookingsCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("lawyerId inconnu du cache - n'incrémente pas specialty-popularity, ne plante pas")
        void recordBookingCreated_unknownLawyer_skipsSpecialtyPopularity() {
            when(dailyBookingStatsRepository.findById(any())).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(lawyerSpecialtyCacheRepository.findById(LAWYER_ID)).thenReturn(Optional.empty());

            analyticsService.recordBookingCreated(LAWYER_ID, DAY, null);

            verifyNoInteractions(specialtyPopularityRepository);
        }

        @Test
        @DisplayName("day null - ignoré entièrement, aucune écriture")
        void recordBookingCreated_nullDay_noWrites() {
            analyticsService.recordBookingCreated(LAWYER_ID, null, LocalTime.of(14, 0));

            verifyNoInteractions(dailyBookingStatsRepository, peakHourStatsRepository,
                    lawyerSpecialtyCacheRepository, specialtyPopularityRepository);
        }
    }

    @Nested
    @DisplayName("recordBookingCancelled")
    class RecordBookingCancelled {

        @Test
        @DisplayName("incrémente le compteur d'annulations du jour, sans toucher bookingsCount")
        void recordBookingCancelled_incrementsCancellationsOnly() {
            DailyBookingStats existing = new DailyBookingStats();
            existing.setDate(DAY);
            existing.setBookingsCount(10L);
            existing.setCancellationsCount(2L);
            when(dailyBookingStatsRepository.findById(DAY)).thenReturn(Optional.of(existing));
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCancelled(DAY);

            assertThat(existing.getCancellationsCount()).isEqualTo(3L);
            assertThat(existing.getBookingsCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("jour inexistant - crée l'entrée avec cancellationsCount=1")
        void recordBookingCancelled_newDay_createsWithCountOne() {
            when(dailyBookingStatsRepository.findById(DAY)).thenReturn(Optional.empty());
            when(dailyBookingStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordBookingCancelled(DAY);

            ArgumentCaptor<DailyBookingStats> captor = ArgumentCaptor.forClass(DailyBookingStats.class);
            verify(dailyBookingStatsRepository).save(captor.capture());
            assertThat(captor.getValue().getCancellationsCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("day null - ignoré, aucune écriture")
        void recordBookingCancelled_nullDay_noWrite() {
            analyticsService.recordBookingCancelled(null);

            verifyNoInteractions(dailyBookingStatsRepository);
        }
    }

    @Nested
    @DisplayName("recordSearch")
    class RecordSearch {

        @Test
        @DisplayName("spécialité et ville fournies - incrémente les deux compteurs indépendamment")
        void recordSearch_bothProvided_incrementsBothCounters() {
            when(searchedSpecialtyStatsRepository.findById("Droit du travail")).thenReturn(java.util.Optional.empty());
            when(searchedSpecialtyStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(searchedCityStatsRepository.findById("Paris")).thenReturn(java.util.Optional.empty());
            when(searchedCityStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordSearch("Droit du travail", "Paris");

            ArgumentCaptor<juribook.audit_service.analytics.entity.SearchedSpecialtyStats> specialtyCaptor =
                    ArgumentCaptor.forClass(juribook.audit_service.analytics.entity.SearchedSpecialtyStats.class);
            verify(searchedSpecialtyStatsRepository).save(specialtyCaptor.capture());
            assertThat(specialtyCaptor.getValue().getSpecialty()).isEqualTo("Droit du travail");
            assertThat(specialtyCaptor.getValue().getSearchCount()).isEqualTo(1L);

            ArgumentCaptor<juribook.audit_service.analytics.entity.SearchedCityStats> cityCaptor =
                    ArgumentCaptor.forClass(juribook.audit_service.analytics.entity.SearchedCityStats.class);
            verify(searchedCityStatsRepository).save(cityCaptor.capture());
            assertThat(cityCaptor.getValue().getCity()).isEqualTo("Paris");
            assertThat(cityCaptor.getValue().getSearchCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("compteur existant - incrémente, ne remplace pas à 1")
        void recordSearch_existingCounter_incrementsExisting() {
            SearchedSpecialtyStats existing = new SearchedSpecialtyStats();
            existing.setSpecialty("Droit du travail");
            existing.setSearchCount(9L);
            when(searchedSpecialtyStatsRepository.findById("Droit du travail")).thenReturn(java.util.Optional.of(existing));
            when(searchedSpecialtyStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordSearch("Droit du travail", null);

            assertThat(existing.getSearchCount()).isEqualTo(10L);
            verifyNoInteractions(searchedCityStatsRepository);
        }

        @Test
        @DisplayName("specialty seule fournie (city null) - n'incrémente que le compteur spécialité")
        void recordSearch_onlySpecialty_incrementsOnlySpecialtyCounter() {
            when(searchedSpecialtyStatsRepository.findById("Droit pénal")).thenReturn(java.util.Optional.empty());
            when(searchedSpecialtyStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordSearch("Droit pénal", null);

            verify(searchedSpecialtyStatsRepository).save(any());
            verifyNoInteractions(searchedCityStatsRepository);
        }

        @Test
        @DisplayName("city seule fournie (specialty null) - n'incrémente que le compteur ville")
        void recordSearch_onlyCity_incrementsOnlyCityCounter() {
            when(searchedCityStatsRepository.findById("Lyon")).thenReturn(java.util.Optional.empty());
            when(searchedCityStatsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            analyticsService.recordSearch(null, "Lyon");

            verify(searchedCityStatsRepository).save(any());
            verifyNoInteractions(searchedSpecialtyStatsRepository);
        }

        @Test
        @DisplayName("aucun filtre (recherche vide) - n'incrémente rien, ne plante pas")
        void recordSearch_noFilters_noWrites() {
            analyticsService.recordSearch(null, null);

            verifyNoInteractions(searchedSpecialtyStatsRepository, searchedCityStatsRepository);
        }

        @Test
        @DisplayName("specialty blanc (espaces) - traité comme absent, pas incrémenté")
        void recordSearch_blankSpecialty_treatedAsAbsent() {
            analyticsService.recordSearch("   ", "Paris");

            verifyNoInteractions(searchedSpecialtyStatsRepository);
        }
    }
}