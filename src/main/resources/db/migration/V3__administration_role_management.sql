ALTER TABLE users
    ADD COLUMN role_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD KEY ix_users_admin_eligibility (role, status, email_verified_at);

ALTER TABLE institutional_policies
    MODIFY COLUMN updated_by_user_id BIGINT UNSIGNED NULL;

ALTER TABLE auth_sessions
    ADD COLUMN source_auth_flow_id BIGINT UNSIGNED NULL,
    ADD UNIQUE KEY uq_auth_sessions_user_source_flow (user_id, source_auth_flow_id),
    ADD CONSTRAINT fk_auth_sessions_source_flow
        FOREIGN KEY (user_id, source_auth_flow_id)
        REFERENCES auth_flows (user_id, id) ON DELETE RESTRICT;

CREATE TABLE administration_guard (
    id TINYINT UNSIGNED NOT NULL,
    policy_seed_operation_id BINARY(16) NULL,
    policy_seeded_at DATETIME(6) NULL,
    first_admin_operation_id BINARY(16) NULL,
    first_admin_user_id BIGINT UNSIGNED NULL,
    first_admin_provisioned_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_administration_guard_singleton CHECK (id = 1),
    CONSTRAINT ck_administration_guard_policy_receipt CHECK (
        (policy_seed_operation_id IS NULL AND policy_seeded_at IS NULL)
        OR
        (policy_seed_operation_id IS NOT NULL AND policy_seeded_at IS NOT NULL)
    ),
    CONSTRAINT ck_administration_guard_admin_receipt CHECK (
        (first_admin_operation_id IS NULL AND first_admin_user_id IS NULL
            AND first_admin_provisioned_at IS NULL)
        OR
        (first_admin_operation_id IS NOT NULL AND first_admin_user_id IS NOT NULL
            AND first_admin_user_id > 0 AND first_admin_provisioned_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

INSERT INTO administration_guard (id) VALUES (1);
