package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.SearchedCityStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchedCityStatsRepository extends JpaRepository<SearchedCityStats, String> {

    List<SearchedCityStats> findAllByOrderBySearchCountDesc();
}