package juribook.audit_service.repository;

import juribook.audit_service.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA pour le journal d'audit.
 *
 * Aucune méthode de recherche/filtrage ajoutée pour l'instant, la
 * consultation (findByActorId, findByTopicAndRecordedAtBetween, etc.)
 * arrive au Sprint 5.8 avec l'API admin. Ce sprint ne fait qu'écrire.
 *
 * ⚠️ Ne jamais appeler deleteById()/delete()/deleteAll() sur ce
 * repository, même si JpaRepository les expose techniquement, le
 * trigger PostgreSQL (V1 migration) les fera de toute façon échouer,
 * mais l'intention est de ne jamais les utiliser du tout.
 */
@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
}