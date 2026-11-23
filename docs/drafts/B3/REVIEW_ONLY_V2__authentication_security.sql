-- UniSpace B3-D10.23 | REVIEW ONLY | MySQL 8.4 / InnoDB
-- DO NOT put this file in src/main/resources/db/migration or execute it.
-- This is a *candidate* for eventual V2__authentication_security.sql after explicit
-- sign-off, disposable-MySQL tests, current-schema verification, and V1 preservation.
-- V1__initial_schema.sql must not be edited.
-- DATETIME(6) values are UTC, supplied/validated by application services.
-- Application checks MUST enforce short lifetimes, atomic consumption,
-- session/account state, device revocation, provider ownership and rate limits.

-- (1 of 3 V1 extensions) A unique composite parent key for provider-link
-- challenges bound to the correct user and authentication session.
ALTER TABLE auth_sessions
    ADD UNIQUE KEY uq_auth_sessions_user_id (user_id, id);

-- (2 of 3 V1 extensions) P-256 public key of an *already verified* device.
-- Preserve V1 device_key_hash; for new devices it must be a documented,
-- validated hex-encoded public-key thumbprint. Do not silently trust old rows
-- with public_key_jwk IS NULL; require secure re-enrollment as appropriate.
ALTER TABLE trusted_devices
    ADD COLUMN public_key_jwk JSON NULL;

-- 1/5: Server-issued Google/Apple challenges (5 min, one-time).
CREATE TABLE provider_auth_challenges (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    challenge_id_digest BINARY(32) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    nonce_digest BINARY(32) NOT NULL,
    state_digest BINARY(32) NULL,
    bound_user_id BIGINT UNSIGNED NULL,
    bound_session_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pac_challenge_digest (challenge_id_digest),
    UNIQUE KEY uq_pac_nonce_digest (nonce_digest),
    KEY ix_pac_expiry (expires_at),
    KEY ix_pac_user_session (bound_user_id, bound_session_id),
    CONSTRAINT fk_pac_user FOREIGN KEY (bound_user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pac_user_session FOREIGN KEY (bound_user_id, bound_session_id)
        REFERENCES auth_sessions (user_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_pac_provider CHECK (provider IN ('GOOGLE', 'APPLE')),
    CONSTRAINT ck_pac_binding CHECK (
        (purpose = 'SIGN_IN' AND bound_user_id IS NULL AND bound_session_id IS NULL)
        OR
        (purpose = 'LINK_PROVIDER' AND bound_user_id IS NOT NULL AND bound_session_id IS NOT NULL)
    ),
    CONSTRAINT ck_pac_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 2/5: Authoritative restricted-flow record (flow JWT maximum 15 min).
-- source_challenge_id is unique: each issued challenge starts at most one flow.
CREATE TABLE auth_flows (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    source_challenge_id BIGINT UNSIGNED NOT NULL,
    flow_jti_digest BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_flows_source (source_challenge_id),
    UNIQUE KEY uq_auth_flows_jti (flow_jti_digest),
    UNIQUE KEY uq_auth_flows_user_id (user_id, id),
    KEY ix_auth_flows_expiry (expires_at),
    CONSTRAINT fk_auth_flows_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_auth_flows_source FOREIGN KEY (source_challenge_id)
        REFERENCES provider_auth_challenges (id) ON DELETE RESTRICT,
    CONSTRAINT ck_auth_flows_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 3/5: Incomplete key enrollment is NOT a trusted device.
-- Eligibility derives from registration OTP or new-device OTP and is checked
-- by the service together with the current auth flow and device challenge.
CREATE TABLE device_enrollments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    auth_flow_id BIGINT UNSIGNED NOT NULL,
    public_key_jwk JSON NULL,
    public_key_thumbprint BINARY(32) NULL,
    authorization_verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_device_enrollments_flow (auth_flow_id),
    UNIQUE KEY uq_device_enrollments_id_flow (id, auth_flow_id),
    KEY ix_device_enrollments_expiry (expires_at),
    CONSTRAINT fk_device_enrollments_flow FOREIGN KEY (auth_flow_id)
        REFERENCES auth_flows (id) ON DELETE RESTRICT,
    CONSTRAINT ck_device_enrollments_key_pair CHECK (
        (public_key_jwk IS NULL AND public_key_thumbprint IS NULL)
        OR (public_key_jwk IS NOT NULL AND public_key_thumbprint IS NOT NULL)
    ),
    CONSTRAINT ck_device_enrollments_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 4/5: Single-use, two-minute ECDSA P-256 possession challenges.
-- Additional user_id enforces ownership of an EXISTING trusted device at
-- database level, not just with application code.
CREATE TABLE device_signature_challenges (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    auth_flow_id BIGINT UNSIGNED NOT NULL,
    device_enrollment_id BIGINT UNSIGNED NULL,
    trusted_device_id BIGINT UNSIGNED NULL,
    challenge_nonce BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_dsc_nonce (challenge_nonce),
    KEY ix_dsc_flow (user_id, auth_flow_id),
    KEY ix_dsc_enrollment_flow (device_enrollment_id, auth_flow_id),
    KEY ix_dsc_user_trusted (user_id, trusted_device_id),
    KEY ix_dsc_expiry (expires_at),
    CONSTRAINT fk_dsc_user_flow FOREIGN KEY (user_id, auth_flow_id)
        REFERENCES auth_flows (user_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_dsc_enrollment_flow FOREIGN KEY (device_enrollment_id, auth_flow_id)
        REFERENCES device_enrollments (id, auth_flow_id) ON DELETE RESTRICT,
    CONSTRAINT fk_dsc_user_trusted FOREIGN KEY (user_id, trusted_device_id)
        REFERENCES trusted_devices (user_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_dsc_single_target CHECK (
        (device_enrollment_id IS NOT NULL AND trusted_device_id IS NULL)
        OR (device_enrollment_id IS NULL AND trusted_device_id IS NOT NULL)
    ),
    CONSTRAINT ck_dsc_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 5/5: Historical *consumed* refresh digests for reuse detection.
-- Keep refresh_token_hash in V1 auth_sessions as the CURRENT digest (hex).
-- Convert one keyed 32-byte digest to hex for that V1 CHAR(64) field; store
-- its raw 32 bytes here. Never persist or log raw refresh tokens.
CREATE TABLE consumed_refresh_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    token_digest BINARY(32) NOT NULL,
    consumed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_consumed_refresh_digest (token_digest),
    KEY ix_consumed_refresh_session_time (session_id, consumed_at),
    CONSTRAINT fk_consumed_refresh_session FOREIGN KEY (session_id)
        REFERENCES auth_sessions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- (3 of 3 V1 extensions) Bind university-email OTPs to a matching user's flow.
-- Nullable for existing V1 rows. The backend MUST require a non-null flow
-- binding on newly issued authentication OTPs and enforce purpose/rate limits.
ALTER TABLE email_challenges
    ADD COLUMN auth_flow_id BIGINT UNSIGNED NULL AFTER user_id,
    ADD KEY ix_email_challenges_user_flow (user_id, auth_flow_id),
    ADD CONSTRAINT fk_email_challenges_user_flow
        FOREIGN KEY (user_id, auth_flow_id)
        REFERENCES auth_flows (user_id, id) ON DELETE RESTRICT;

-- End of D10.23 REVIEW ONLY candidate. No data backfill or production run.
