package juribook.audit_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournit explicitement un bean ObjectMapper (Jackson 2 classique).
 *
 * Même fix que notification-service : Spring Boot 4 utilise
 * Jackson 3 (JsonMapper) en interne, mais ce service n'a même pas de
 * @RestController pour déclencher ce mécanisme, AuditEventConsumer en a
 * besoin explicitement pour parser les payloads Kafka en JSON générique.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}