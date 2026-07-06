package juribook.audit_service.abuse.repository;

import juribook.audit_service.abuse.entity.AbuseSignal;
import juribook.audit_service.abuse.entity.SignalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AbuseSignalRepository extends JpaRepository<AbuseSignal, Long> {

    long countByActorIdAndSignalTypeAndOccurredAtAfter(
        Long actorId, SignalType signalType, LocalDateTime after);
}