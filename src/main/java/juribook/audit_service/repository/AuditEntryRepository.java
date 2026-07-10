package juribook.audit_service.repository;

import juribook.audit_service.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository JPA pour le journal d'audit.
 *
 * ⚠️ Ne jamais appeler deleteById()/delete()/deleteAll() sur ce
 * repository, même si JpaRepository les expose techniquement, le
 * trigger PostgreSQL (V1 migration) les fera de toute façon échouer,
 * mais l'intention est de ne jamais les utiliser du tout.
 */
@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /**
     * Consultation filtrée : tous les paramètres sont
     * optionnels et cumulables (pattern déjà utilisé dans
     * LawyerRepository.search côté lawyer-service).
     *
     * Le filtre de date compare COALESCE(occurredAt, recordedAt) plutôt
     * que occurredAt seul : occurredAt est une extraction best-effort
     * (AuditService), absente si le payload ne contenait pas ce champ
     * ou n'était pas parsable. Sans ce repli, ces entrées seraient
     * invisibles à toute recherche par plage de dates, alors qu'on sait
     * quand même quand elles ont été enregistrées (recordedAt).
     */
    @Query("""
        SELECT a FROM AuditEntry a
        WHERE (:actorId IS NULL OR a.actorId = :actorId)
          AND (:from IS NULL OR COALESCE(a.occurredAt, a.recordedAt) >= :from)
          AND (:to IS NULL OR COALESCE(a.occurredAt, a.recordedAt) <= :to)
        ORDER BY COALESCE(a.occurredAt, a.recordedAt) DESC
    """)
    Page<AuditEntry> search(
        @Param("actorId") Long actorId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    /**
     * Historique complet d'une réservation.
     *
     * Aucune colonne dédiée "bookingId" en base, le champ n'existe que
     * dans le payload JSON brut (booking.created/confirmed/rejected/
     * cancelled, document.uploaded/ready, etc. portent tous bookingId).
     * Recherche par expression régulière PostgreSQL (~) plutôt qu'un
     * LIKE '%bookingId":123%' simple, pour ne PAS confondre bookingId=12
     * avec bookingId=123 ou bookingId=1234 (le LIKE seul matcherait les
     * trois pour une recherche sur "12"). Le groupe [,}] borne la valeur
     * numérique à droite : soit une virgule (autre champ JSON après),
     * soit une accolade fermante (dernier champ du payload).
     *
     * nativeQuery=true assumé : ce projet est déjà couplé à PostgreSQL
     * spécifiquement (triggers append-only V1), donc pas de perte de
     * portabilité supplémentaire à utiliser l'opérateur ~ ici.
     *
     * Retourne une List (pas de pagination) triée chronologiquement
     * croissant : un historique de réservation se lit comme une
     * timeline (créée → confirmée → documents → terminée), pas comme
     * un flux "plus récent d'abord" à parcourir page par page.
     */
    @Query(value = """
        SELECT * FROM audit_entries
        WHERE payload ~ CONCAT('"bookingId":', CAST(:bookingId AS text), '[,}]')
        ORDER BY COALESCE(occurred_at, recorded_at) ASC
        """, nativeQuery = true)
    List<AuditEntry> findByBookingId(@Param("bookingId") Long bookingId);
}