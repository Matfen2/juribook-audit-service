# juribook-audit-service

Journal d'audit immutable pour **JuriBook** : consomme les 8 topics Kafka de la plateforme et persiste chaque événement dans une table append-only, traçabilité complète, conformité légale, preuve en cas de litige (UC-K1 du cahier des charges). Détecte automatiquement les comportements abusifs, alimente un pipeline d'analytics temps réel, et expose désormais une consultation admin filtrée.

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Security · JWT (validation des tokens émis par l'auth-service, lecture seule du rôle)
- Spring Kafka (**consumer sur les 8 topics** - ce service ne produisait aucun événement, cf. Kafka ci-dessous)
- PostgreSQL 16 · Flyway (migrations)
- Springdoc OpenAPI (Swagger UI)
- Port : **8085**

## Structure du projet

```
src/main/java/juribook/audit_service/
├── abuse/
│   ├── entity/
│   │   ├── AbuseSignal.java            # Signal individuel (annulation, avis 1★)
│   │   └── AbuseAlert.java             # Alerte générée quand un seuil est franchi
│   ├── repository/
│   │   ├── AbuseSignalRepository.java
│   │   └── AbuseAlertRepository.java
│   ├── service/
│   │   └── AbuseDetectionService.java  # Seuils : >5 annulations/7j, >3 avis 1★/24h
│   └── event/
│       ├── BookingSignalConsumer.java  # Écoute booking-events (booking.cancelled)
│       ├── ReviewSignalConsumer.java   # Écoute review-events (review.created, rating=1)
│       ├── AbuseEvent.java             # Payload publié sur abuse-events (abuse.detected)
│       ├── AbuseEventPublisher.java
│       └── AbuseEventPublisherImpl.java
├── analytics/
│   ├── entity/
│   │   ├── LawyerSpecialtyCache.java       # Cache lawyerId→specialty (alimenté par lawyer-events)
│   │   ├── DailyBookingStats.java          # Réservations/annulations par jour
│   │   ├── SpecialtyPopularity.java        # Spécialités les plus RÉSERVÉES
│   │   ├── PeakHourStats.java               # Réservations par heure de RENDEZ-VOUS
│   │   ├── SearchedSpecialtyStats.java     # Spécialités les plus RECHERCHÉES
│   │   └── SearchedCityStats.java          # Villes les plus recherchées
│   ├── repository/
│   │   ├── LawyerSpecialtyCacheRepository.java
│   │   ├── DailyBookingStatsRepository.java
│   │   ├── SpecialtyPopularityRepository.java
│   │   ├── PeakHourStatsRepository.java
│   │   ├── SearchedSpecialtyStatsRepository.java
│   │   └── SearchedCityStatsRepository.java
│   ├── event/
│   │   ├── LawyerSpecialtyCacheConsumer.java  # Écoute lawyer-events (lawyer.approved)
│   │   ├── BookingAnalyticsConsumer.java      # Écoute booking-events (created/cancelled)
│   │   └── SearchEventConsumer.java           # Écoute search-events (search.performed)
│   ├── service/
│   │   ├── AnalyticsService.java           # Écriture : upsert des 6 vues matérialisées
│   │   └── AnalyticsQueryService.java      # Lecture : agrégation pour les 6 endpoints
│   ├── dto/response/
│   │   ├── DailyBookingStatsResponse.java
│   │   ├── CancellationRateResponse.java
│   │   ├── SpecialtyPopularityResponse.java
│   │   ├── PeakHourResponse.java
│   │   ├── SearchedSpecialtyResponse.java
│   │   └── SearchedCityResponse.java
│   └── controller/
│       └── AnalyticsController.java        # GET /api/audit/analytics/**
├── config/
│   ├── SecurityConfig.java             # /api/audit/** → hasRole("ADMIN"), /actuator/health → permitAll
│   ├── OpenApiConfig.java              # Configuration Swagger UI
│   ├── JacksonConfig.java              # Bean ObjectMapper explicite (même fix que notification-service)
│   └── SchedulingConfig.java
├── controller/
│   └── AuditController.java            # GET /api/audit, GET /api/audit/booking/{bookingId}
├── dto/response/
│   └── AuditEntryResponse.java
├── entity/
│   └── AuditEntry.java                 # Une entrée par événement Kafka reçu, tous topics confondus
├── event/
│   └── AuditEventConsumer.java         # @KafkaListener unique sur les 8 topics
├── repository/
│   └── AuditEntryRepository.java       # Aucune méthode de suppression/modification exposée + search paginé + findByBookingId
├── security/
│   └── JwtService.java                 # Validation lecture seule - pas de UserRepository (pas de table User ici)
├── filter/
│   └── JwtAuthenticationFilter.java    # Dépend uniquement de JwtService, contrairement à auth-service
└── service/
    └── AuditService.java               # Extraction best-effort + écriture append-only + consultation filtrée
src/main/resources/
├── application.yaml
└── db/migration/
    ├── V1__create_audit_entries_table.sql        # Table + triggers PostgreSQL append-only
    ├── V2__create_abuse_signals_and_alerts_tables.sql
    ├── V3__create_analytics_tables.sql            # 4 vues matérialisées
    └── V4__create_search_trend_tables.sql         # 2 vues matérialisées supplémentaires
```

## Lancer en local (hors Docker)

```bash
# Prérequis : PostgreSQL sur localhost:5436 avec la base auditdb, Kafka actif
mvn spring-boot:run
```

Comme `notification-service`, Kafka n'est **jamais désactivé** ici, c'est la raison d'être de ce service, sans broker actif, aucun événement n'est jamais reçu, mais le service démarre quand même normalement (le consumer retente la connexion en arrière-plan, rien ne se passe tant que Kafka n'est pas up).

## Lancer via Docker Compose

```bash
# Depuis juribook-docker/docker/
docker compose up -d postgres-audit audit-service
```

## Swagger UI

[http://localhost:8085/swagger-ui.html](http://localhost:8085/swagger-ui.html)

## Health check

[http://localhost:8085/actuator/health](http://localhost:8085/actuator/health)

---

## Endpoints

Tous réservés au rôle **ADMIN** (`/api/audit/**` → `hasRole("ADMIN")` dans `SecurityConfig`), sauf `/actuator/health`.

### Journal d'audit

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/audit` | Consultation filtrée paginée - `userId`/`from`/`to` optionnels et cumulables |
| `GET` | `/api/audit/booking/{bookingId}` | Historique complet d'une réservation, triée chronologiquement, non paginé |

### Analytics

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/api/audit/analytics/daily-bookings` | Réservations/annulations par jour, `from`/`to` optionnels (défaut : 30 derniers jours) |
| `GET` | `/api/audit/analytics/cancellation-rate` | Taux d'annulation agrégé sur la période |
| `GET` | `/api/audit/analytics/specialty-popularity` | Spécialités les plus **réservées** |
| `GET` | `/api/audit/analytics/peak-hours` | Réservations par heure de rendez-vous (0-23, toutes les heures incluses) |
| `GET` | `/api/audit/analytics/searched-specialties` | Spécialités les plus **recherchées** |
| `GET` | `/api/audit/analytics/searched-cities` | Villes les plus recherchées |

---

## Exemples

### Historique complet d'une réservation
```
GET http://localhost:8085/api/audit/booking/900
Authorization: Bearer <token_jwt_admin>
```
Réponse - 200, liste triée chronologiquement, pas de pagination :
```json
[
    {
        "id": 12,
        "topic": "booking-events",
        "eventType": "booking.created",
        "actorId": 4,
        "payload": "{\"eventType\":\"booking.created\",\"bookingId\":900,...}",
        "occurredAt": "2026-07-01T09:00:00",
        "recordedAt": "2026-07-01T09:00:01"
    }
]
```
⚠️ Recherche par expression régulière PostgreSQL sur le payload JSON brut (`payload ~ '"bookingId":900[,}]'`), pas de colonne `bookingId` dédiée en base, ne renvoie que les événements dont le producteur a effectivement inclus ce champ. Liste vide (`[]`, code 200) si aucun événement ne correspond, pas une erreur 404.

### Spécialités réservées vs recherchées
```
GET http://localhost:8085/api/audit/analytics/specialty-popularity
GET http://localhost:8085/api/audit/analytics/searched-specialties
Authorization: Bearer <token_jwt_admin>
```
Deux questions différentes : le premier compte les **réservations** effectives, le second les **recherches** (publié par `lawyer-service` à chaque `GET /api/lawyers`, même sans résultat), une spécialité très recherchée mais jamais réservée peut révéler un manque d'avocats disponibles.

⚠️ Le premier utilise le **nom affichable** (`"Droit du travail"`), le second le **slug** (`"droit-du-travail"`), formats différents, incohérence assumée côté `lawyer-service`.

---

## Ce que fait ce service

**Un seul `@KafkaListener`** (`AuditEventConsumer`), abonné aux 8 topics provisionnés :
```
booking-events, slot-events, lawyer-events, review-events,
audit-events, search-events, document-events, abuse-events
```

Pour chaque message reçu, `AuditService.recordEvent` :
1. Sauvegarde le **payload JSON brut intégral**, jamais perdu, jamais réinterprété, c'est la source de vérité.
2. Tente d'enrichir best-effort trois champs de confort pour la lecture humaine :
   - `eventType`, extrait tel quel si le champ existe dans le payload.
   - `actorId`, premier champ trouvé parmi `clientId`, `lawyerId`, `userId`, `authUserId`, `actorId` (dans cet ordre). Peut être ambigu sur un événement qui a plusieurs de ces champs (ex: `booking.created` a `clientId` **et** `lawyerId`), en cas de doute, le payload complet fait foi.
   - `occurredAt`, parsé si présent et valide, distinct de `recordedAt` (l'horodatage métier côté producteur, vs quand l'audit-service l'a reçu).
3. Persiste, que l'enrichissement ait réussi ou non, un payload illisible en JSON, ou d'un type totalement inconnu, est quand même tracé brut. **Rien ne fait jamais échouer le consumer** : bloquer sur un problème de parsing ferait perdre tous les événements suivants, pas juste celui-là.

**Producteurs réels, désormais tous les 8 topics** :

| Topic | Producteur | Depuis |
|---|---|---|
| `booking-events` | `booking-service` |
| `slot-events` | `booking-service` |
| `lawyer-events` | `lawyer-service`, `auth-service` |
| `review-events` | `lawyer-service` |
| `abuse-events` | `audit-service` (ce service) |
| `search-events` | `lawyer-service` |
| `document-events` | `booking-service` | - |
| `audit-events` | - | pas encore de producteur identifié |

---

## Détection d'abus automatique

Deux seuils fixes, vérifiés à chaque signal reçu (pas de job planifié) :

| Seuil | Signal source | Topic écouté |
|---|---|---|
| Plus de 5 annulations en 7 jours | `booking.cancelled` | `booking-events` (`BookingSignalConsumer`) |
| Plus de 3 avis 1★ en 24h | `review.created` avec `rating=1` | `review-events` (`ReviewSignalConsumer`) |

Franchissement d'un seuil → `AbuseDetectionService` crée une `AbuseAlert` et publie `abuse.detected` sur `abuse-events`, **premier événement produit par ce service**, jusque-là uniquement consommateur. Consommé par `auth-service` (`AbuseEventConsumer`), qui suspend automatiquement le compte concerné (`SuspensionSource.ABUSE_DETECTION`, cf. README `auth-service`).

Pas de doublon d'alerte pour un même utilisateur tant que l'alerte précédente n'a pas été résolue.

---

## Append-only - garanti à deux niveaux

**1. Applicatif** : `AuditEntryRepository` n'expose aucune méthode de suppression/modification *utilisée* dans le code (hérite techniquement de `delete()`/`deleteById()` via `JpaRepository`, mais rien ne les appelle jamais).

**2. Base de données** : la vraie garantie, indépendante d'une éventuelle erreur de code future — deux triggers PostgreSQL (migration `V1`) qui lèvent une exception sur tout `UPDATE` ou `DELETE` :
```sql
CREATE TRIGGER audit_entries_no_update
    BEFORE UPDATE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_entries_modification();

CREATE TRIGGER audit_entries_no_delete
    BEFORE DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_entries_modification();
```
Même une requête SQL manuelle directe sur la base échoue. Vérifiable :
```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "DELETE FROM audit_entries WHERE id = 1;"
```
→ `ERROR: audit_entries est append-only : UPDATE et DELETE sont interdits (tentative sur id=1)`

---

## Commandes SQL utiles

### Voir les dernières entrées, tous topics confondus

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT id, topic, event_type, actor_id, occurred_at, recorded_at FROM audit_entries ORDER BY recorded_at DESC LIMIT 20;"
```

### Compter les événements par topic

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT topic, COUNT(*) FROM audit_entries GROUP BY topic ORDER BY COUNT(*) DESC;"
```

### Historique complet d'un acteur (ex: litige à trancher)

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT topic, event_type, payload, occurred_at FROM audit_entries WHERE actor_id = 4 ORDER BY occurred_at ASC;"
```

### Voir le payload brut complet d'une entrée

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT payload FROM audit_entries WHERE id = 1;"
```

### Historique complet d'une réservation

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT topic, event_type, recorded_at FROM audit_entries WHERE payload ~ '\"bookingId\":900[,}]' ORDER BY recorded_at ASC;"
```

### Inspecter les vues matérialisées analytics

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT * FROM daily_booking_stats ORDER BY date DESC LIMIT 10;"
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT * FROM specialty_popularity ORDER BY bookings_count DESC;"
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT * FROM searched_specialty_stats ORDER BY search_count DESC;"
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT * FROM searched_city_stats ORDER BY search_count DESC;"
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT * FROM peak_hour_stats ORDER BY hour_of_day;"
```

### Voir les alertes d'abus actives

```bash
docker exec -it juribook-postgres-audit psql -U juribook -d auditdb -c "SELECT id, user_id, signal_type, signal_count, created_at FROM abuse_alerts ORDER BY created_at DESC;"
```

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5436/auditdb` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |
| `JWT_SECRET` | Secret JWT partagé avec l'auth-service | valeur de dev |

---

## Notes techniques

### `spring-boot-starter-webmvc-test` - piège de pom.xml

Deux erreurs opposées rencontrées à la suite en essayant de faire compiler `@WebMvcTest` :
1. Une première tentative avait ajouté 5 artefacts `*-test` par module (`spring-boot-starter-actuator-test`, `-data-jpa-test`, `-flyway-test`, `-kafka-test`, `-webmvc-test`), seul le dernier existe réellement dans l'écosystème Maven.
2. Après les avoir retirés en pensant qu'aucun n'existait, `@WebMvcTest` ne compilait plus du tout, `spring-boot-starter-webmvc-test` **est** bien réel et indispensable en Spring Boot 4, confirmé par inspection directe du jar `spring-boot-test-autoconfigure-4.1.0.jar` (qui ne contient pas `WebMvcTest.class`) : le package réel est `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`.

Dépendances de test qui doivent être présentes : `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-kafka-test`, `spring-security-test`.

### `JwtAuthenticationFilter` n'a qu'une seule dépendance

Contrairement à `auth-service` (qui dépend aussi de `UserRepository` pour vérifier `enabled`), le filtre JWT d'`audit-service` ne dépend que de `JwtService`, ce service n'a pas de table `User`, le rôle est extrait directement du token sans vérification en base.

---

## Limites connues

- **`actorId` est une heuristique, pas une garantie** : sur un événement avec plusieurs identités possibles (ex: `booking.created` a `clientId` **et** `lawyerId`), seul le premier trouvé dans l'ordre de priorité est retenu. Pour une recherche fiable "tout ce qui concerne l'utilisateur X, quel que soit son rôle dans l'événement", il faudra chercher dans le payload (LIKE ou requête JSON PostgreSQL), pas uniquement filtrer sur `actor_id`.
- **`audit-events` n'a toujours aucun producteur identifié**, le topic est écouté, mais rien ne le publie à ce stade du projet.
- **La recherche par `bookingId` dépend entièrement de la présence du champ dans le payload JSON brut**, un futur type d'événement lié à une réservation qui omettrait ce champ resterait invisible à `GET /api/audit/booking/{id}`, sans erreur.
- **`specialty_popularity` (réservations) et `searched_specialty_stats` (recherches) utilisent des formats de clé différents** (nom affichable vs slug), nécessite une résolution manuelle pour les croiser sous une même clé.
- **Aucune purge ni archivage d'`audit_entries`**, la table grandit indéfiniment par construction (append-only), pas de stratégie de rétention définie à ce stade.
- **Les vues matérialisées analytics n'ont pas de mécanisme de recalcul/réconciliation** : un message Kafka manqué (redémarrage au mauvais moment, erreur de désérialisation) laisse un compteur durablement sous-estimé, sans alerte.