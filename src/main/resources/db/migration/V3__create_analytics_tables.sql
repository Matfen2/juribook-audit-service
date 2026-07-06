-- Tables matérialisées alimentées en temps réel par
-- AnalyticsService (audit-service), à partir de lawyer-events et
-- booking-events, jamais par requête sur les tables transactionnelles
-- des autres services.

-- Cache lawyerId → specialty, alimenté par lawyer-events (lawyer.approved).
-- Nécessaire car booking-events ne porte que lawyerId, pas la spécialité.
CREATE TABLE lawyer_specialty_cache (
    lawyer_id  BIGINT PRIMARY KEY,
    specialty  VARCHAR(100) NOT NULL
);

-- Réservations et annulations par jour (jour de l'ÉVÉNEMENT, pas du
-- rendez-vous — mesure d'activité de la plateforme).
CREATE TABLE daily_booking_stats (
    date                 DATE PRIMARY KEY,
    bookings_count       BIGINT NOT NULL DEFAULT 0,
    cancellations_count  BIGINT NOT NULL DEFAULT 0
);

-- Nombre de réservations par spécialité, résolue via lawyer_specialty_cache.
CREATE TABLE specialty_popularity (
    specialty       VARCHAR(100) PRIMARY KEY,
    bookings_count  BIGINT NOT NULL DEFAULT 0
);

-- Nombre de réservations par heure de RENDEZ-VOUS (0-23), à partir de
-- slotStartTime — pas l'heure de création de la réservation.
CREATE TABLE peak_hour_stats (
    hour_of_day     INT PRIMARY KEY CHECK (hour_of_day BETWEEN 0 AND 23),
    bookings_count  BIGINT NOT NULL DEFAULT 0
);