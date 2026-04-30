CREATE TABLE auth_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
        password_hash VARCHAR NOT NULL,
        is_active BOOLEAN NOT NULL,
        is_verified BOOLEAN NOT NULL,
        last_login TIMESTAMPTZ NULL,
        created_at TIMESTAMPTZ NOT NULL,
        updated_at TIMESTAMPTZ NOT NULL
    );

    CREATE TABLE auth_roles (
        id UUID PRIMARY KEY,
        name VARCHAR(64) NOT NULL UNIQUE
    );

    CREATE TABLE auth_users_roles (
        user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
        role_id UUID NOT NULL REFERENCES auth_roles(id) ON DELETE RESTRICT,
        PRIMARY KEY (user_id, role_id)
    );

    CREATE TABLE auth_verification_tokens (
        id UUID PRIMARY KEY,
        user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
        token VARCHAR NOT NULL,
        type VARCHAR(32) NOT NULL,
        session_id UUID NOT NULL,
        expires_at TIMESTAMPTZ NOT NULL,
        consumed_at TIMESTAMPTZ NULL,
        attempts INT NOT NULL DEFAULT 0,
        last_attempt_at TIMESTAMPTZ NULL,
        last_sent_at TIMESTAMPTZ NOT NULL,
        created_at TIMESTAMPTZ NOT NULL,
        CONSTRAINT uq_auth_verification_tokens_session_id UNIQUE (session_id)
    );

    CREATE TABLE auth_outbox_events (
        id UUID PRIMARY KEY,
        aggregate_type VARCHAR(64) NOT NULL,
        aggregate_id UUID NOT NULL,
        event_type VARCHAR(64) NOT NULL,
        topic VARCHAR(128) NOT NULL,
        key VARCHAR(128) NOT NULL,
        payload_json TEXT NOT NULL,
        status VARCHAR(16) NOT NULL,
        attempts INT NOT NULL DEFAULT 0,
        last_error TEXT NULL,
        created_at TIMESTAMPTZ NOT NULL,
        updated_at TIMESTAMPTZ NOT NULL,
        sent_at TIMESTAMPTZ NULL,
        CONSTRAINT auth_outbox_events_status_check
            CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED', 'DEAD'))
    );

    INSERT INTO auth_roles (id, name)
    VALUES ('00000000-0000-0000-0000-000000000001', 'SUPERADMIN')
    ON CONFLICT (name) DO NOTHING;

    INSERT INTO auth_roles (id, name)
    VALUES ('00000000-0000-0000-0000-000000000002', 'ADMIN')
    ON CONFLICT (name) DO NOTHING;

    INSERT INTO auth_roles (id, name)
    VALUES ('00000000-0000-0000-0000-000000000003', 'USER')
    ON CONFLICT (name) DO NOTHING;

    CREATE INDEX idx_auth_users_email ON auth_users(email);
    CREATE INDEX idx_auth_users_roles_role_id ON auth_users_roles(role_id);
    CREATE INDEX idx_auth_verification_tokens_user_id ON auth_verification_tokens(user_id);
    CREATE INDEX idx_auth_verification_tokens_token ON auth_verification_tokens(token);
    CREATE INDEX idx_verif_tokens_session_type ON auth_verification_tokens(session_id, type);
    CREATE INDEX idx_auth_verification_tokens_user_type_created
        ON auth_verification_tokens(user_id, type, created_at DESC);
    CREATE INDEX idx_auth_outbox_events_status_updated_at
        ON auth_outbox_events (status, updated_at);
    CREATE INDEX idx_auth_outbox_events_created_at
        ON auth_outbox_events (created_at);