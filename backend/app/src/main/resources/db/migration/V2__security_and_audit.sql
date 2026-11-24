CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE app_user (
    username VARCHAR(120) PRIMARY KEY,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    username VARCHAR(120) NOT NULL REFERENCES app_user(username),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL
);
CREATE INDEX refresh_token_user_idx ON refresh_token(username);

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    resource VARCHAR(240) NOT NULL
);
CREATE INDEX audit_log_time_idx ON audit_log(occurred_at);

INSERT INTO app_user (username, password_hash, role)
SELECT 'demo-user', '${demoUserHash}', 'USER'
WHERE '${demoUserHash}' <> '';

INSERT INTO app_user (username, password_hash, role)
SELECT 'demo-admin', '${demoAdminHash}', 'ADMIN'
WHERE '${demoAdminHash}' <> '';
