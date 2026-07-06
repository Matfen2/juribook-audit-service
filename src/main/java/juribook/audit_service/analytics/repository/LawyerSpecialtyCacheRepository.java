package juribook.audit_service.analytics.repository;

import juribook.audit_service.analytics.entity.LawyerSpecialtyCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LawyerSpecialtyCacheRepository extends JpaRepository<LawyerSpecialtyCache, Long> {
}