package juribook.audit_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entrée du journal d'audit : une par événement Kafka reçu, tous topics
 * confondus.
 */
@Entity
@Table(name = "audit_entries")
@Data
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Topic Kafka d'origine (booking-events, slot-events, lawyer-events,
    // review-events, audit-events, search-events, document-events,
    // abuse-events).
    @Column(name = "topic", nullable = false, length = 50)
    private String topic;

    // Extrait du champ "eventType" du payload JSON, si présent.
    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "actor_id")
    private Long actorId;

    // Payload JSON brut, intégral, tel que publié par le producteur.
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        this.recordedAt = LocalDateTime.now();
    }
}