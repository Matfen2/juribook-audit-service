package juribook.audit_service.abuse.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Un signal = un événement individuel potentiellement suspect (une
 * annulation, un avis 1-étoile). Table distincte de audit_entries car
 * responsabilité différente (agrégation active pour décision métier,
 * pas simple journalisation passive), même si les deux vivent dans le
 * même service pour respecter la contrainte des 6 microservices.
 */
@Entity
@Table(name = "abuse_signals")
@Data
public class AbuseSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 30)
    private SignalType signalType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        this.occurredAt = LocalDateTime.now();
    }
}