package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.DailyBookingStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyBookingStatsRepository extends JpaRepository<DailyBookingStats, LocalDate> {

    List<DailyBookingStats> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}