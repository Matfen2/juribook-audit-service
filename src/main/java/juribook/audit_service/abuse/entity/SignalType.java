package juribook.audit_service.abuse.entity;

public enum SignalType {
    BOOKING_CANCELLED,   // booking.cancelled sur booking-events
    LOW_RATING_REVIEW    // review.created avec rating=1 sur review-events
}