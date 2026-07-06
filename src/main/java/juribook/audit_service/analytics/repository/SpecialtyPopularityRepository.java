package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.SpecialtyPopularity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecialtyPopularityRepository extends JpaRepository<SpecialtyPopularity, String> {

    List<SpecialtyPopularity> findAllByOrderByBookingsCountDesc();
}