package juribook.audit_service.analytics.dto.response;

public record PeakHourResponse(int hourOfDay, long bookingsCount) {
}