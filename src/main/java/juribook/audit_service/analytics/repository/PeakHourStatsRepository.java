package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.PeakHourStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PeakHourStatsRepository extends JpaRepository<PeakHourStats, Integer> {
}