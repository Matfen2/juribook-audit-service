package juribook.audit_service.analytics.dto.response;

import java.time.LocalDate;

public record CancellationRateResponse(
    LocalDate from,
    LocalDate to,
    long totalBookings,
    long totalCancellations,
    double cancellationRate
) {
}