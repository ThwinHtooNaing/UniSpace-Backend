-- B2 v1.0 implementation draft. See docs/development/database.md for review gates.
-- DATETIME(6) values are UTC. Callers supply timestamps explicitly.

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    first_name VARCHAR(100) NULL,
    last_name VARCHAR(100) NULL,
    student_id VARCHAR(64) NULL,
    student_id_normalized VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
    university_email VARCHAR(254) NULL,
    email_normalized VARCHAR(254) COLLATE utf8mb4_0900_bin NULL,
    email_verified_at DATETIME(6) NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_student_id (student_id_normalized),
    UNIQUE KEY uq_users_email (email_normalized),
    CONSTRAINT ck_users_active_verified CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE provider_identities (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_subject VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_subject (provider, provider_subject),
    UNIQUE KEY uq_provider_user (user_id, provider),
    CONSTRAINT fk_provider_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE trusted_devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    device_key_hash CHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_devices_hash (user_id, device_key_hash),
    UNIQUE KEY uq_devices_user_id (user_id, id),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE auth_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NULL,
    refresh_token_hash CHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_refreshed_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_sessions_refresh_hash (refresh_token_hash),
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_auth_sessions_device FOREIGN KEY (user_id, device_id)
        REFERENCES trusted_devices (user_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE email_challenges (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    destination_email VARCHAR(254) NOT NULL,
    code_digest VARBINARY(32) NOT NULL,
    attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_available_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    invalidated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_email_challenges_user_purpose (user_id, purpose, created_at),
    CONSTRAINT fk_email_challenges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_email_challenges_attempts CHECK (attempt_count <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE institutional_policies (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_id_pattern VARCHAR(512) NOT NULL,
    allowed_email_domains JSON NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    updated_by_user_id BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_policies_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE role_requests (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT UNSIGNED NOT NULL,
    requested_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    decided_by_user_id BIGINT UNSIGNED NULL,
    decision_note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY ix_role_requests_status (status, created_at),
    KEY ix_role_requests_requester (requester_user_id, created_at),
    CONSTRAINT fk_role_requests_requester FOREIGN KEY (requester_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_requests_decider FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE channels (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    name_normalized VARCHAR(120) NOT NULL,
    creator_user_id BIGINT UNSIGNED NOT NULL,
    owner_user_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_channels_name (name_normalized),
    CONSTRAINT fk_channels_creator FOREIGN KEY (creator_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_channels_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE channel_memberships (
    user_id BIGINT UNSIGNED NOT NULL,
    channel_id BIGINT UNSIGNED NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_memberships_channel_user (channel_id, user_id),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_memberships_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- MySQL checks FKs immediately. Set the owner after inserting their membership,
-- and transfer or clear ownership before deleting the current owner's membership.
ALTER TABLE channels ADD CONSTRAINT fk_channels_owner_membership FOREIGN KEY (id, owner_user_id)
    REFERENCES channel_memberships (channel_id, user_id) ON DELETE RESTRICT;

CREATE TABLE membership_requests (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT UNSIGNED NOT NULL,
    target_channel_id BIGINT UNSIGNED NULL,
    target_channel_id_snapshot BIGINT UNSIGNED NOT NULL,
    source_channel_id BIGINT UNSIGNED NULL,
    source_channel_id_snapshot BIGINT UNSIGNED NULL,
    request_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_by_user_id BIGINT UNSIGNED NULL,
    decided_at DATETIME(6) NULL,
    pending_target_channel_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN target_channel_id END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_membership_requests_pending (requester_user_id, pending_target_channel_id),
    KEY ix_membership_requests_target (target_channel_id, status, requested_at),
    KEY ix_membership_requests_requester (requester_user_id, status),
    CONSTRAINT fk_membership_requests_requester FOREIGN KEY (requester_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_requests_target FOREIGN KEY (target_channel_id) REFERENCES channels (id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_requests_source FOREIGN KEY (source_channel_id) REFERENCES channels (id) ON DELETE RESTRICT,
    CONSTRAINT fk_membership_requests_decider FOREIGN KEY (decided_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_membership_requests_pending_target CHECK (status <> 'PENDING' OR target_channel_id IS NOT NULL),
    CONSTRAINT ck_membership_requests_target_snapshot CHECK (
        target_channel_id IS NULL OR target_channel_id = target_channel_id_snapshot
    ),
    CONSTRAINT ck_membership_requests_source_snapshot CHECK (
        source_channel_id IS NULL OR (source_channel_id_snapshot IS NOT NULL AND source_channel_id = source_channel_id_snapshot)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE files (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    channel_id BIGINT UNSIGNED NULL,
    uploader_user_id BIGINT UNSIGNED NOT NULL,
    filename VARCHAR(255) NOT NULL,
    filename_normalized VARCHAR(255) NOT NULL,
    file_category VARCHAR(16) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT UNSIGNED NULL,
    storage_key VARCHAR(512) COLLATE utf8mb4_0900_bin NULL,
    duration_ms BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL,
    restore_channel_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    trashed_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    active_filename VARCHAR(255) COLLATE utf8mb4_0900_as_ci
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN filename_normalized END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_files_storage_key (storage_key),
    UNIQUE KEY uq_files_active_filename (channel_id, active_filename),
    KEY ix_files_channel_created (channel_id, status, created_at),
    KEY ix_files_channel_filename (channel_id, status, filename_normalized),
    KEY ix_files_channel_size (channel_id, status, size_bytes),
    CONSTRAINT fk_files_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE RESTRICT,
    CONSTRAINT fk_files_uploader FOREIGN KEY (uploader_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_files_restore_channel FOREIGN KEY (restore_channel_id) REFERENCES channels (id) ON DELETE RESTRICT,
    CONSTRAINT ck_files_channel_required CHECK (status = 'DELETED' OR channel_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE upload_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id BIGINT UNSIGNED NOT NULL,
    storage_upload_id VARCHAR(255) COLLATE utf8mb4_0900_bin NULL,
    idempotency_key VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upload_sessions_idempotency (user_id, idempotency_key),
    KEY ix_upload_sessions_device_state (device_id, state),
    KEY ix_upload_sessions_state_expiry (state, expires_at),
    CONSTRAINT fk_upload_sessions_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE RESTRICT,
    CONSTRAINT fk_upload_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_upload_sessions_device FOREIGN KEY (user_id, device_id)
        REFERENCES trusted_devices (user_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE upload_parts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    upload_session_id BIGINT UNSIGNED NOT NULL,
    part_number INT UNSIGNED NOT NULL,
    etag VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_upload_parts_session_number (upload_session_id, part_number),
    CONSTRAINT fk_upload_parts_session FOREIGN KEY (upload_session_id) REFERENCES upload_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE video_progress (
    user_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    position_ms BIGINT UNSIGNED NOT NULL,
    completed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, file_id),
    CONSTRAINT fk_video_progress_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_video_progress_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    event_key VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    category VARCHAR(32) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notifications_user_event (user_id, event_key),
    KEY ix_notifications_inbox (user_id, deleted_at, created_at),
    KEY ix_notifications_unread (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE notification_preferences (
    user_id BIGINT UNSIGNED NOT NULL,
    category VARCHAR(32) NOT NULL,
    push_enabled BOOLEAN NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, category),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE audit_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
    details JSON NOT NULL,
    correlation_id VARCHAR(64) COLLATE utf8mb4_0900_bin NULL,
    occurred_at DATETIME(6) NOT NULL,
    retention_until DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_events_actor_time (actor_user_id, occurred_at),
    KEY ix_audit_events_target (target_type, target_id),
    KEY ix_audit_events_retention (retention_until),
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE outbox_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    event_version SMALLINT UNSIGNED NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    claimed_by VARCHAR(100) NULL,
    claim_expires_at DATETIME(6) NULL,
    deduplication_key VARCHAR(128) COLLATE utf8mb4_0900_bin NULL,
    created_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_outbox_events_deduplication (deduplication_key),
    KEY ix_outbox_events_due (status, next_attempt_at, id),
    KEY ix_outbox_events_lease (status, claim_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
