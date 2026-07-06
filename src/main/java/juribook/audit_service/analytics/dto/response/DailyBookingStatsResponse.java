package juribook.audit_service.analytics.dto.response;

import java.time.LocalDate;

public record DailyBookingStatsResponse(LocalDate date, long bookingsCount, long cancellationsCount) {
}