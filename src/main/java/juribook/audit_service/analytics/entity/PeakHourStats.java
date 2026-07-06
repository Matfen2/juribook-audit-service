package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Vue matérialisée : nombre de réservations par heure de RENDEZ-VOUS
 * (0-23), à partir de slotStartTime porté par booking.created (Sprint
 * 7.4 — pas l'heure de création de la réservation, cf. décision produit).
 */
@Entity
@Table(name = "peak_hour_stats")
@Data
public class PeakHourStats {

    @Id
    private Integer hourOfDay;

    @Column(nullable = false)
    private long bookingsCount = 0L;
}