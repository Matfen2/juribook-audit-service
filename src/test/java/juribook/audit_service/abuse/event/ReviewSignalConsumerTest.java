package juribook.audit_service.abuse.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import juribook.audit_service.abuse.service.AbuseDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewSignalConsumerTest {

    @Mock
    private AbuseDetectionService abuseDetectionService;

    private ReviewSignalConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReviewSignalConsumer(new ObjectMapper().findAndRegisterModules(), abuseDetectionService);
    }

    @Test
    @DisplayName("review.created avec rating=1 - enregistre le signal")
    void onReviewEvent_oneStarRating_recordsLowRatingReview() {
        String payload = """
            {"eventType":"review.created","reviewId":12,"lawyerId":4,"clientId":42,"rating":1}
            """;

        consumer.onReviewEvent(payload);

        verify(abuseDetectionService).recordLowRatingReview(42L, 12L);
    }

    @Test
    @DisplayName("review.created avec rating=5 - ignoré, pas un signal d'abus")
    void onReviewEvent_fiveStarRating_ignored() {
        String payload = """
            {"eventType":"review.created","reviewId":12,"lawyerId":4,"clientId":42,"rating":5}
            """;

        consumer.onReviewEvent(payload);

        verify(abuseDetectionService, never()).recordLowRatingReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("review.created avec rating=2 - ignoré (seul rating=1 compte)")
    void onReviewEvent_twoStarRating_ignored() {
        String payload = """
            {"eventType":"review.created","reviewId":12,"lawyerId":4,"clientId":42,"rating":2}
            """;

        consumer.onReviewEvent(payload);

        verify(abuseDetectionService, never()).recordLowRatingReview(anyLong(), anyLong());
    }

    @Test
    @DisplayName("JSON malformé - ne plante jamais")
    void onReviewEvent_malformedJson_doesNotThrow() {
        assertDoesNotThrow(() -> consumer.onReviewEvent("pas du JSON"));
        verify(abuseDetectionService, never()).recordLowRatingReview(anyLong(), anyLong());
    }
}