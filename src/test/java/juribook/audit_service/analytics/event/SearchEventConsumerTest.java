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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchEventConsumer")
class SearchEventConsumerTest {

    @Mock
    private AnalyticsService analyticsService;

    private SearchEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SearchEventConsumer(new ObjectMapper().findAndRegisterModules(), analyticsService);
    }

    @Test
    @DisplayName("search.performed avec specialty et city - délègue les deux")
    void onSearchEvent_withSpecialtyAndCity_delegatesBoth() {
        String payload = """
            {"eventType":"search.performed","specialty":"Droit du travail","city":"Paris","query":"licenciement"}
            """;

        consumer.onSearchEvent(payload);

        verify(analyticsService).recordSearch("Droit du travail", "Paris");
    }

    @Test
    @DisplayName("search.performed sans filtre - délègue avec null/null, ne plante pas")
    void onSearchEvent_noFilters_delegatesWithNulls() {
        String payload = """
            {"eventType":"search.performed","specialty":null,"city":null,"query":null}
            """;

        assertDoesNotThrow(() -> consumer.onSearchEvent(payload));

        verify(analyticsService).recordSearch(isNull(), isNull());
    }

    @Test
    @DisplayName("eventType différent - ignoré")
    void onSearchEvent_wrongEventType_ignored() {
        String payload = """
            {"eventType":"other.event","specialty":"Droit pénal","city":"Lyon"}
            """;

        consumer.onSearchEvent(payload);

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("JSON malformé - ne plante jamais")
    void onSearchEvent_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onSearchEvent("pas du JSON"));
        verifyNoInteractions(analyticsService);
    }
}