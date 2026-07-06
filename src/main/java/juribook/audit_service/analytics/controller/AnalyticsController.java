package juribook.audit_service.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import juribook.audit_service.analytics.dto.response.CancellationRateResponse;
import juribook.audit_service.analytics.dto.response.DailyBookingStatsResponse;
import juribook.audit_service.analytics.dto.response.PeakHourResponse;
import juribook.audit_service.analytics.dto.response.SearchedCityResponse;
import juribook.audit_service.analytics.dto.response.SearchedSpecialtyResponse;
import juribook.audit_service.analytics.dto.response.SpecialtyPopularityResponse;
import juribook.audit_service.analytics.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Routes couvertes par SecurityConfig via le pattern déjà existant
 * "/api/audit/**" → hasRole("ADMIN") - pas de modification nécessaire.
 */
@RestController
@RequestMapping("/api/audit/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Vues matérialisées temps réel - ADMIN uniquement")
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    @GetMapping("/daily-bookings")
    @Operation(
        summary = "Réservations et annulations par jour",
        description = "Sans from/to : 30 derniers jours par défaut."
    )
    public ResponseEntity<List<DailyBookingStatsResponse>> getDailyBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsQueryService.getDailyStats(from, to));
    }

    @GetMapping("/cancellation-rate")
    @Operation(
        summary = "Taux d'annulation agrégé sur une période",
        description = "Sans from/to : 30 derniers jours par défaut."
    )
    public ResponseEntity<CancellationRateResponse> getCancellationRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(analyticsQueryService.getCancellationRate(from, to));
    }

    @GetMapping("/specialty-popularity")
    @Operation(summary = "Spécialités les plus demandées, triées par nombre de réservations décroissant")
    public ResponseEntity<List<SpecialtyPopularityResponse>> getSpecialtyPopularity() {
        return ResponseEntity.ok(analyticsQueryService.getSpecialtyPopularity());
    }

    @GetMapping("/peak-hours")
    @Operation(summary = "Répartition des réservations par heure de rendez-vous (0-23, y compris les heures à 0)")
    public ResponseEntity<List<PeakHourResponse>> getPeakHours() {
        return ResponseEntity.ok(analyticsQueryService.getPeakHours());
    }

    @GetMapping("/searched-specialties")
    @Operation(
        summary = "Spécialités les plus RECHERCHÉES",
        description = "Distinct de /specialty-popularity (réservations) - basé sur search-events, pas booking-events."
    )
    public ResponseEntity<List<SearchedSpecialtyResponse>> getSearchedSpecialties() {
        return ResponseEntity.ok(analyticsQueryService.getSearchedSpecialties());
    }

    @GetMapping("/searched-cities")
    @Operation(summary = "Villes les plus recherchées")
    public ResponseEntity<List<SearchedCityResponse>> getSearchedCities() {
        return ResponseEntity.ok(analyticsQueryService.getSearchedCities());
    }
}