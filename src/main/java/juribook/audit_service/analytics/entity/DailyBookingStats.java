package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

/**
 * Vue matérialisée : réservations créées et annulées par jour.
 * date = jour de l'ÉVÉNEMENT (occurredAt), pas le jour du rendez-vous,
 * c'est une mesure d'activité de la plateforme ("combien de réservations
 * ont été faites/annulées ce jour-là"), distincte de "heures de pointe"
 * qui elle porte sur l'heure du rendez-vous.
 */
@Entity
@Table(name = "daily_booking_stats")
@Data
public class DailyBookingStats {

    @Id
    private LocalDate date;

    @Column(nullable = false)
    private long bookingsCount = 0L;

    @Column(nullable = false)
    private long cancellationsCount = 0L;
}