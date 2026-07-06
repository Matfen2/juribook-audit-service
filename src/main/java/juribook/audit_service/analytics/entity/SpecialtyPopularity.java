package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Vue matérialisée : nombre de réservations par spécialité, résolue via
 * LawyerSpecialtyCache à partir du lawyerId porté par booking.created.
 */
@Entity
@Table(name = "specialty_popularity")
@Data
public class SpecialtyPopularity {

    @Id
    @Column(length = 100)
    private String specialty;

    @Column(nullable = false)
    private long bookingsCount = 0L;
}