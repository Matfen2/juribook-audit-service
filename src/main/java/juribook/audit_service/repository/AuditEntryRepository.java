package juribook.audit_service.repository;

import juribook.audit_service.entity.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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
}