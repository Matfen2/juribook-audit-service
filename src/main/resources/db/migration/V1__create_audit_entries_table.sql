CREATE TABLE audit_entries (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(50) NOT NULL,
    event_type   VARCHAR(50),
    actor_id     BIGINT,
    payload      TEXT NOT NULL,
    occurred_at  TIMESTAMP,
    recorded_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entries_actor ON audit_entries(actor_id);
CREATE INDEX idx_audit_entries_topic ON audit_entries(topic);
CREATE INDEX idx_audit_entries_recorded_at ON audit_entries(recorded_at DESC);

CREATE OR REPLACE FUNCTION prevent_audit_entries_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries est append-only : UPDATE et DELETE sont interdits (tentative sur id=%)',
        COALESCE(OLD.id, NULL);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_no_update
    BEFORE UPDATE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_entries_modification();

CREATE TRIGGER audit_entries_no_delete
    BEFORE DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_entries_modification();