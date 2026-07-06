-- Compteurs de tendances de recherche, alimentés par
-- search-events (publié par lawyer-service à chaque recherche).
-- Distinct de specialty_popularity (V3, qui compte les RÉSERVATIONS) —
-- une spécialité très recherchée mais jamais réservée ici peut révéler
-- un manque d'avocats disponibles dans cette spécialité/ville.

CREATE TABLE searched_specialty_stats (
    specialty     VARCHAR(100) PRIMARY KEY,
    search_count  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE searched_city_stats (
    city          VARCHAR(100) PRIMARY KEY,
    search_count  BIGINT NOT NULL DEFAULT 0
);
