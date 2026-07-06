package juribook.audit_service.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** Vue matérialisée : nombre de RECHERCHES par ville, à partir de search-events. */
@Entity
@Table(name = "searched_city_stats")
@Data
public class SearchedCityStats {

    @Id
    @Column(length = 100)
    private String city;

    @Column(nullable = false)
    private long searchCount = 0L;
}