# juribook-audit-service

Journal d'audit immutable pour **JuriBook** : consomme les 8 topics Kafka de la plateforme et persiste chaque événement dans une table append-only, traçabilité complète, conformité légale, preuve en cas de litige (UC-K1 du cahier des charges).

## Stack

- Java 21 · Spring Boot 4.1.0 · Maven
- Spring Kafka (consumer : ce service ne produit aucun événement)
- PostgreSQL 16 · Flyway (migrations)
- Port : **8085**

## Structure du projet

```
src/main/java/juribook/audit_service/
├── config/
│   └── JacksonConfig.java          # Bean ObjectMapper explicite (même fix que notification-service)
├── entity/
│   └── AuditEntry.java             # Une entrée par événement Kafka reçu, tous topics confondus
├── event/
│   └── AuditEventConsumer.java     # @KafkaListener unique sur les 8 topics
├── repository/
│   └── AuditEntryRepository.java   # Aucune méthode de suppression/modification exposée
├── service/
│   └── AuditService.java           # Extraction best-effort + écriture append-only
└── AuditServiceApplication.java
src/main/resources/
├── application.yaml
└── db/migration/
    └── V1__create_audit_entries_table.sql   # Table + triggers PostgreSQL append-only
```

## Lancer en local (hors Docker)

```bash
# Prérequis : PostgreSQL sur localhost:5436 avec la base auditdb, Kafka actif
mvn spring-boot:run
```

Comme `notification-service`, Kafka n'est **jamais désactivé** ici, c'est la raison d'être de ce service.

## Lancer via Docker Compose

```bash
# Depuis juribook-docker/docker/
docker compose up -d
```

## Health check

[http://localhost:8085/actuator/health](http://localhost:8085/actuator/health)

Pas de Swagger UI ni d'API REST métier ce sprint, aucun `@RestController`. La consultation (`GET /api/audit?...`) arrive au Sprint 5.8.

---

## Ce que fait ce service

**Un seul `@KafkaListener`**, abonné aux 8 topics provisionnés au Sprint 5.1 :
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

**Actuellement, seuls `booking-events` et `slot-events` ont de vrais producteurs** (`booking-service`). Les 6 autres topics sont déjà écoutés en avance — dès qu'un futur sprint (5.9, 6.x, 7.x) ajoutera des producteurs sur `lawyer-events`/`review-events`/etc., ils seront automatiquement capturés sans aucune modification de ce service.

---

## Append-only — garanti à deux niveaux

**1. Applicatif** : `AuditEntryRepository` n'expose aucune méthode de suppression/modification *utilisée* dans le code (hérite techniquement de `delete()`/`deleteById()` via `JpaRepository`, mais rien ne les appelle jamais).

**2. Base de données** : la vraie garantie, indépendante d'une éventuelle erreur de code future : deux triggers PostgreSQL (migration `V1`) qui lèvent une exception sur tout `UPDATE` ou `DELETE` :
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

---

## Variables d'environnement

| Variable | Description | Valeur par défaut |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL PostgreSQL | `jdbc:postgresql://localhost:5436/auditdb` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Adresse Kafka | `localhost:9092` |

---

## Limites connues

- **`actorId` est une heuristique, pas une garantie** : sur un événement avec plusieurs identités possibles (ex: `booking.created` a `clientId` et `lawyerId`), seul le premier trouvé dans l'ordre de priorité est retenu. Pour une recherche fiable "tout ce qui concerne l'utilisateur X, quel que soit son rôle dans l'événement", il faudra chercher dans `payload` (LIKE ou requête JSON PostgreSQL), pas uniquement filtrer sur `actor_id`.
- **Pas encore d'API de consultation**, la table est remplie mais rien ne l'expose encore en HTTP. C'est le scope du Sprint 5.8.
- **Aucune donnée de test pour 6 des 8 topics** (`lawyer-events`, `review-events`, `audit-events`, `search-events`, `document-events`, `abuse-events`), le listener les écoute, mais rien ne les publie encore dans le reste de la plateforme. Normal à ce stade, pas un bug.
- **`init-auditdb.sql` est un placeholder** — le contenu exact des scripts d'init des autres bases n'était pas disponible au moment d'écrire ce service ; à harmoniser si les autres font plus qu'un commentaire.