package juribook.audit_service.analytics.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LawyerSpecialtyCacheConsumer")
class LawyerSpecialtyCacheConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    private LawyerSpecialtyCacheConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LawyerSpecialtyCacheConsumer(new ObjectMapper().findAndRegisterModules(), analyticsService);
    }

    @Test
    @DisplayName("lawyer.approved avec specialty - met à jour le cache")
    void onLawyerEvent_approved_updatesCache() {
        String payload = """
            {"eventType":"lawyer.approved","lawyerId":10,"email":"sophie@example.com","specialty":"Droit du travail"}
            """;

        consumer.onLawyerEvent(payload);

        verify(analyticsService).upsertLawyerSpecialty(10L, "Droit du travail");
    }

    @Test
    @DisplayName("lawyer.rejected - ignoré, pas de mise à jour du cache")
    void onLawyerEvent_rejected_ignored() {
        String payload = """
            {"eventType":"lawyer.rejected","lawyerId":10,"reason":"motif"}
            """;

        consumer.onLawyerEvent(payload);

        verify(analyticsService, never()).upsertLawyerSpecialty(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("specialty absente - ignoré, ne plante pas")
    void onLawyerEvent_missingSpecialty_ignoredGracefully() {
        String payload = """
            {"eventType":"lawyer.approved","lawyerId":10}
            """;

        assertDoesNotThrow(() -> consumer.onLawyerEvent(payload));
        verify(analyticsService, never()).upsertLawyerSpecialty(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("JSON malformé - ne plante jamais")
    void onLawyerEvent_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onLawyerEvent("pas du JSON"));
        verify(analyticsService, never()).upsertLawyerSpecialty(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}