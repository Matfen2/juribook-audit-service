package juribook.audit_service.analytics.controller;

import juribook.audit_service.analytics.dto.response.CancellationRateResponse;
import juribook.audit_service.analytics.dto.response.DailyBookingStatsResponse;
import juribook.audit_service.analytics.dto.response.PeakHourResponse;
import juribook.audit_service.analytics.dto.response.SearchedCityResponse;
import juribook.audit_service.analytics.dto.response.SearchedSpecialtyResponse;
import juribook.audit_service.analytics.dto.response.SpecialtyPopularityResponse;
import juribook.audit_service.analytics.service.AnalyticsQueryService;
import juribook.audit_service.config.SecurityConfig;
import juribook.audit_service.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration Web pour AnalyticsController.
 *
 * ⚠️ Mêmes leçons que AdminUserControllerTest (auth-service):
 *   - @Import(SecurityConfig.class) nécessaire, @WebMvcTest ne scanne
 *     pas les @Configuration classiques par défaut.
 *   - Authentification via SecurityMockMvcRequestPostProcessors.user(...)
 *     plutôt que @WithMockUser, pour les mêmes raisons de fiabilité.
 *   - 403 (pas 401) attendu sans authentification : pas
 *     d'AuthenticationEntryPoint custom dans SecurityConfig.
 *
 * JwtAuthenticationFilter d'audit-service ne dépend que de JwtService
 * (pas de UserRepository, ce service n'a pas de table User, le rôle
 * est extrait directement du JWT), donc un seul @MockitoBean nécessaire
 * ici, contrairement à auth-service qui en demandait deux.
 */
@WebMvcTest(AnalyticsController.class)
@Import(SecurityConfig.class)
@DisplayName("AnalyticsController - Tests d'intégration Web")
class AnalyticsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnalyticsQueryService analyticsQueryService;

    @MockitoBean
    JwtService jwtService;

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin@test.com").roles("ADMIN");
    }

    private static RequestPostProcessor client() {
        return SecurityMockMvcRequestPostProcessors.user("client@test.com").roles("CLIENT");
    }

    @Nested
    @DisplayName("Sécurité par rôle")
    class RoleSecurity {

        @Test
        @DisplayName("❌ 403 - sans authentification")
        void getDailyBookings_noAuthentication_returns403() throws Exception {
            mockMvc.perform(get("/api/audit/analytics/daily-bookings"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(analyticsQueryService);
        }

        @Test
        @DisplayName("❌ 403 - authentifié en CLIENT, refusé")
        void getDailyBookings_clientRole_returns403() throws Exception {
            mockMvc.perform(get("/api/audit/analytics/daily-bookings").with(client()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(analyticsQueryService);
        }

        @Test
        @DisplayName("✅ 200 - authentifié en ADMIN, autorisé")
        void getDailyBookings_adminRole_returns200() throws Exception {
            when(analyticsQueryService.getDailyStats(any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/audit/analytics/daily-bookings").with(admin()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/daily-bookings")
    class DailyBookings {

        @Test
        @DisplayName("sans from/to - délègue avec null, laisse le service appliquer le défaut 30 jours")
        void getDailyBookings_noParams_delegatesWithNulls() throws Exception {
            when(analyticsQueryService.getDailyStats(null, null)).thenReturn(List.of());

            mockMvc.perform(get("/api/audit/analytics/daily-bookings").with(admin()))
                    .andExpect(status().isOk());

            verify(analyticsQueryService).getDailyStats(null, null);
        }

        @Test
        @DisplayName("avec from/to - transmet les dates parsées")
        void getDailyBookings_withParams_parsesAndDelegates() throws Exception {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 5);
            when(analyticsQueryService.getDailyStats(from, to)).thenReturn(
                    List.of(new DailyBookingStatsResponse(from, 5, 1)));

            mockMvc.perform(get("/api/audit/analytics/daily-bookings")
                            .with(admin())
                            .param("from", "2026-07-01")
                            .param("to", "2026-07-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bookingsCount").value(5))
                    .andExpect(jsonPath("$[0].cancellationsCount").value(1));

            verify(analyticsQueryService).getDailyStats(from, to);
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/cancellation-rate")
    class CancellationRate {

        @Test
        @DisplayName("retourne le taux calculé par le service")
        void getCancellationRate_returnsServiceResult() throws Exception {
            LocalDate from = LocalDate.of(2026, 7, 1);
            LocalDate to = LocalDate.of(2026, 7, 5);
            when(analyticsQueryService.getCancellationRate(from, to))
                    .thenReturn(new CancellationRateResponse(from, to, 20, 5, 0.25));

            mockMvc.perform(get("/api/audit/analytics/cancellation-rate")
                            .with(admin())
                            .param("from", "2026-07-01")
                            .param("to", "2026-07-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBookings").value(20))
                    .andExpect(jsonPath("$.totalCancellations").value(5))
                    .andExpect(jsonPath("$.cancellationRate").value(0.25));
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/specialty-popularity")
    class SpecialtyPopularityEndpoint {

        @Test
        @DisplayName("retourne la liste triée renvoyée par le service")
        void getSpecialtyPopularity_returnsServiceList() throws Exception {
            when(analyticsQueryService.getSpecialtyPopularity()).thenReturn(
                    List.of(new SpecialtyPopularityResponse("Droit du travail", 50)));

            mockMvc.perform(get("/api/audit/analytics/specialty-popularity").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].specialty").value("Droit du travail"))
                    .andExpect(jsonPath("$[0].bookingsCount").value(50));
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/peak-hours")
    class PeakHoursEndpoint {

        @Test
        @DisplayName("retourne les 24 heures renvoyées par le service")
        void getPeakHours_returns24Hours() throws Exception {
            when(analyticsQueryService.getPeakHours()).thenReturn(
                    java.util.stream.IntStream.range(0, 24)
                            .mapToObj(h -> new PeakHourResponse(h, h == 14 ? 7 : 0))
                            .toList());

            mockMvc.perform(get("/api/audit/analytics/peak-hours").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(24)))
                    .andExpect(jsonPath("$[14].bookingsCount").value(7));
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/searched-specialties")
    class SearchedSpecialtiesEndpoint {

        @Test
        @DisplayName("retourne la liste triée renvoyée par le service")
        void getSearchedSpecialties_returnsServiceList() throws Exception {
            when(analyticsQueryService.getSearchedSpecialties()).thenReturn(
                    List.of(new SearchedSpecialtyResponse("Droit du travail", 120)));

            mockMvc.perform(get("/api/audit/analytics/searched-specialties").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].specialty").value("Droit du travail"))
                    .andExpect(jsonPath("$[0].searchCount").value(120));
        }

        @Test
        @DisplayName("❌ 403 - refusé sans authentification")
        void getSearchedSpecialties_noAuth_returns403() throws Exception {
            mockMvc.perform(get("/api/audit/analytics/searched-specialties"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(analyticsQueryService);
        }
    }

    @Nested
    @DisplayName("GET /api/audit/analytics/searched-cities")
    class SearchedCitiesEndpoint {

        @Test
        @DisplayName("retourne la liste triée renvoyée par le service")
        void getSearchedCities_returnsServiceList() throws Exception {
            when(analyticsQueryService.getSearchedCities()).thenReturn(
                    List.of(new SearchedCityResponse("Paris", 80)));

            mockMvc.perform(get("/api/audit/analytics/searched-cities").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].city").value("Paris"))
                    .andExpect(jsonPath("$[0].searchCount").value(80));
        }
    }
}