CREATE TABLE email_jobs (
    event_id        UUID PRIMARY KEY,
    correlation_id  VARCHAR(64) NULL,

    type            VARCHAR(64) NOT NULL,
    template        VARCHAR(64) NOT NULL,

    to_email_fp     VARCHAR(64) NOT NULL,

    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    attempts        INT NOT NULL DEFAULT 0,
    last_error      TEXT NULL,

    payload_enc     BYTEA NOT NULL,
    payload_nonce   BYTEA NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    sent_at         TIMESTAMPTZ NULL
);

CREATE INDEX idx_email_jobs_status ON email_jobs (status);
CREATE INDEX idx_email_jobs_updated_at ON email_jobs (updated_at);
CREATE INDEX idx_email_jobs_created_at ON email_jobs (created_at);
CREATE INDEX idx_email_jobs_to_email_fp ON email_jobs (to_email_fp);