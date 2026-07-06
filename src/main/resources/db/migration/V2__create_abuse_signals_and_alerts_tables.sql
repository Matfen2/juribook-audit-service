CREATE TABLE abuse_signals (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT NOT NULL,
    signal_type  VARCHAR(30) NOT NULL,
    source_id    BIGINT,
    occurred_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_abuse_signals_actor_type_time ON abuse_signals(actor_id, signal_type, occurred_at);

CREATE TABLE abuse_alerts (
    id           BIGSERIAL PRIMARY KEY,
    actor_id     BIGINT NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_abuse_alerts_actor_reason_time ON abuse_alerts(actor_id, reason, triggered_at);