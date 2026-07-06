package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.SearchedSpecialtyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchedSpecialtyStatsRepository extends JpaRepository<SearchedSpecialtyStats, String> {

    List<SearchedSpecialtyStats> findAllByOrderBySearchCountDesc();
}