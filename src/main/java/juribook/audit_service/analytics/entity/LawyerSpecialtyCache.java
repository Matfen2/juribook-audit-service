package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Cache lawyerId → specialty, alimenté par lawyer-events (eventType
 * lawyer.approved). Nécessaire pour la métrique "spécialités populaires"
 * : booking-events ne porte que lawyerId, pas la spécialité.
 *
 * ⚠️ Reflète User.specialty (auth-service, simple String), pas
 * Lawyer.specialties (lawyer-service, ManyToMany) — les deux ne sont pas
 * forcément synchronisés entre eux.
 */
@Entity
@Table(name = "lawyer_specialty_cache")
@Data
public class LawyerSpecialtyCache {

    @Id
    private Long lawyerId;

    @Column(nullable = false, length = 100)
    private String specialty;
}