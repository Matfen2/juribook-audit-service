package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Vue matérialisée : nombre de RECHERCHES par spécialité (Sprint 7.5),
 * à partir de search-events. Distinct de SpecialtyPopularity (Sprint 7.4,
 * compte les réservations), une spécialité très recherchée mais jamais
 * réservée ici peut révéler un manque d'avocats disponibles.
 */
@Entity
@Table(name = "searched_specialty_stats")
@Data
public class SearchedSpecialtyStats {

    @Id
    @Column(length = 100)
    private String specialty;

    @Column(nullable = false)
    private long searchCount = 0L;
}