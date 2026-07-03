package juribook.audit_service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import juribook.audit_service.dto.response.AuditEntryResponse;
import juribook.audit_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Controller REST de consultation du journal d'audit.
 *
 * Réservé au rôle ADMIN (contrôlé dans SecurityConfig, pas via
 * @PreAuthorize ici, cohérent avec le pattern déjà utilisé côté
 * AdminController de l'auth-service pour les routes /api/admin/**).
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Consultation du journal d'audit - ADMIN uniquement")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @Operation(
        summary = "Consulter le journal d'audit",
        description = """
            Retourne l'historique d'audit filtré, paginé. Tous les
            paramètres sont optionnels et cumulables, sans filtre,
            retourne tout le journal (paginé).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page de résultats"),
        @ApiResponse(responseCode = "401", description = "Token absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Rôle insuffisant (ADMIN requis)")
    })
    public ResponseEntity<Page<AuditEntryResponse>> search(

        @Parameter(description = "Filtre sur l'acteur (clientId/lawyerId/authUserId selon l'événement)")
        @RequestParam(required = false) Long userId,

        @Parameter(description = "Borne inférieure (incluse), format ISO 8601 - ex: 2026-07-01T00:00:00")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

        @Parameter(description = "Borne supérieure (incluse), format ISO 8601")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

        @Parameter(description = "Numéro de page (0-based, défaut : 0)")
        @RequestParam(defaultValue = "0") int page,

        @Parameter(description = "Résultats par page (défaut : 20, max : 50)")
        @RequestParam(defaultValue = "20") int size

    ) {
        return ResponseEntity.ok(auditService.search(userId, from, to, page, size));
    }
}