package juribook.audit_service.abuse.repository;

import juribook.audit_service.abuse.entity.AbuseAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AbuseAlertRepository extends JpaRepository<AbuseAlert, Long> {

    boolean existsByActorIdAndReasonAndTriggeredAtAfter(
        Long actorId, String reason, LocalDateTime after);
}