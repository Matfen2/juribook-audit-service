package juribook.audit_service.abuse.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Dédoublonnage - une alerte par acteur/motif/fenêtre, évite de
 * republier abuse.detected à chaque nouveau signal une fois le seuil
 * déjà franchi dans la fenêtre en cours.
 */
@Entity
@Table(name = "abuse_alerts")
@Data
public class AbuseAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "triggered_at", nullable = false, updatable = false)
    private LocalDateTime triggeredAt;

    @PrePersist
    protected void onCreate() {
        this.triggeredAt = LocalDateTime.now();
    }
}