package juribook.audit_service.controller;

import juribook.audit_service.config.SecurityConfig;
import juribook.audit_service.dto.response.AuditEntryResponse;
import juribook.audit_service.security.JwtService;
import juribook.audit_service.service.AuditService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de AuditController.getBookingHistory (Sprint 7.7) — fichier
 * séparé de l'AuditControllerTest existant (non fourni), même pattern
 * de sécurité que le reste du projet : @Import(SecurityConfig.class) +
 * SecurityMockMvcRequestPostProcessors (@WithMockUser seul ne survit
 * pas à une vraie SecurityFilterChain). @MockitoBean JwtService
 * uniquement : audit-service n'a qu'une seule dépendance de sécurité
 * (pas de UserRepository, cf. JwtAuthenticationFilter).
 *
 * Pas d'ObjectMapper autowired ici : toutes les requêtes sont des GET
 * sans corps, rien à sérialiser côté test — @WebMvcTest ne fournit de
 * toute façon pas le bean ObjectMapper custom d'audit-service (défini
 * dans JacksonConfig, une @Configuration non scannée par @WebMvcTest
 * sans @Import explicite), ce qui aurait fait échouer l'injection.
 */
@WebMvcTest(AuditController.class)
@Import(SecurityConfig.class)
@DisplayName("AuditController.getBookingHistory (Sprint 7.7)")
class AuditControllerBookingHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private JwtService jwtService;

    private RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN");
    }

    @Nested
    @DisplayName("GET /api/audit/booking/{bookingId}")
    class GetBookingHistory {

        @Test
        @DisplayName("retourne l'historique renvoyé par le service, trié chronologiquement")
        void getBookingHistory_returnsServiceResult() throws Exception {
            AuditEntryResponse created = new AuditEntryResponse(
                    1L, "booking-events", "booking.created", 42L,
                    "{\"eventType\":\"booking.created\",\"bookingId\":900}",
                    LocalDateTime.of(2026, 7, 1, 9, 0), LocalDateTime.of(2026, 7, 1, 9, 0));

            when(auditService.getBookingHistory(900L)).thenReturn(List.of(created));

            mockMvc.perform(get("/api/audit/booking/900").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].eventType").value("booking.created"));
        }

        @Test
        @DisplayName("aucun événement trouvé - retourne une liste vide en 200, pas 404")
        void getBookingHistory_noEvents_returnsEmptyList200() throws Exception {
            when(auditService.getBookingHistory(999L)).thenReturn(List.of());

            mockMvc.perform(get("/api/audit/booking/999").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("❌ 403 - refusé sans authentification")
        void getBookingHistory_noAuth_returns403() throws Exception {
            mockMvc.perform(get("/api/audit/booking/900"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("❌ 403 - refusé pour un rôle non-ADMIN (CLIENT)")
        void getBookingHistory_clientRole_returns403() throws Exception {
            mockMvc.perform(get("/api/audit/booking/900")
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CLIENT")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(auditService);
        }
    }
}