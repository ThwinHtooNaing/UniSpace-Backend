# UniSpace — B3-D10.23 Flyway V2 Migration SQL Review

**Status:** REVIEW CANDIDATE; SQL not approved, applied, or deployed  
**Candidate file:** `REVIEW_ONLY_V2__authentication_security.sql` (intentionally **not** a Flyway `V2__...sql` filename)  
**Source:** uploaded `V1__initial_schema.sql` (18 V1 application tables)  
**Design authority:** B3-D10.22 working design approved: five new tables and three existing-table extensions  
**API scope:** 14 working mobile-facing authentication endpoints; no implementation authorization  
**Product scope:** approved 167 FRs unchanged; Stage 4.3 Group A UX unchanged

**B3-I02 review status.** D10.25–D10.28 update the security assumptions for reviewing this candidate. The SQL and its historical verification record retain D10.23 status. The duplicate-window test expectation in the D10.24 historical runbook is superseded by D10.27. [Current authentication decisions](../../architecture/B3/UniSpace_B3_Current_Authentication_Decisions.md)

## 1. What the SQL contains

| Change | Table | Purpose |
|---|---|---|
| Add unique composite key | `auth_sessions` | `(user_id, id)` supports account-bound provider-linking challenges |
| Add nullable JSON column | `trusted_devices` | Server-validated P-256 public JWK for *verified* trusted devices |
| Create | `provider_auth_challenges` | Bound Google/Apple sign-in/link challenge, nonce/state digest, expiry/consumption |
| Create | `auth_flows` | Authoritative restricted-flow JWT lifetime/invalidation record |
| Create | `device_enrollments` | Nontrusted, pending enrollment with public key and verification eligibility |
| Create | `device_signature_challenges` | One-time signature challenge bound to enrollment **or** existing user-owned device |
| Create | `consumed_refresh_tokens` | Digests of previously rotated refresh tokens; historical replay detection |
| Add column, index, composite FK | `email_challenges` | Optional V1-compatible flow binding plus exact owner consistency |

The new `device_signature_challenges.user_id` is a deliberate *schema-level ownership constraint* needed to enforce both `auth_flow` and trusted-device account binding via composite foreign keys. It adds no user-facing feature or endpoint.

## 2. Security and migration rules

1. **Immutable V1:** Do not edit `V1__initial_schema.sql`, change its Flyway checksum, or reset the working database.
2. **No unverified trust:** `trusted_devices.verified_at` remains nonnull in V1, so only insert a trusted-device record **after** successful OTP eligibility **and** valid P-256 signature proof. Pending enrollment exists separately.
3. **Existing devices:** Existing V1 records may have `public_key_jwk IS NULL`; they must **not** be assumed to possess a P-256 key. Verify existing data and select an explicit secure re-enrollment path before deployment. The existing `device_key_hash` remains unchanged; D10.26 requires an RFC 7638 SHA-256 public JWK thumbprint for verified new keys. Review the mapping of the digest to this legacy column.
4. **OTP:** Existing `email_challenges` rows remain valid at the schema level because `auth_flow_id` is nullable. New OTP issuing services must require a matching user/flow and enforce 10-minute expiry, 60-second resend, five failures, replacement, and single-use transactions.
5. **Auth and device challenges:** Five-minute provider and two-minute signature limits are enforced by the application against authoritative UTC timestamps, alongside atomic consumption and rate limiting. Database `CHECK`s guard positive expiry and association, not exact protocol duration.
6. **Restricted flows:** A valid JWT alone is insufficient; verify unexpired, noncompleted, noninvalidated flow record, correct audience/purpose/current account and step permissions. Flow JWTs have 15-minute maximum and no refresh token.
7. **Refresh:** `auth_sessions.refresh_token_hash` is existing `CHAR(64)`; the new consumed table stores the **same keyed 32-byte digest** as `BINARY(32)`. Convert to/from a fixed lowercase hex representation consistently; never use unkeyed hashes for low-entropy secrets, and never log credentials. Keep consumption history while needed for replay detection.
8. **Atomic refresh:** With MySQL transaction locking, verify current secret, add previous digest to consumed history, rotate the current digest, and update sliding inactivity timestamp/expiry atomically. Every recognized consumed digest identifies its affected family for immediate revocation, even on a simultaneous request or retry after a lost response. Commit revocation before reporting replay. Keep other devices’ sessions valid. Review the assumption that the session row is the stable family identifier across rotations.
9. **Owner-bound linking:** `LINK_PROVIDER` challenges require ACTIVE authorization and matching `(bound_user_id,bound_session_id)`; `SIGN_IN` challenges must not bind to an existing session. No provider email-only account merging.
10. **Provider scope.** D10.28 selects Google for the first implementation. Preserve Apple contracts and defer live Apple integration, including its Android browser handoff. Retain the 14 mobile operations.

## 3. Items still needing review **before** SQL sign-off

- **Key and challenge binding.** Verify how the candidate represents RFC 7638 thumbprints, the opaque device handle and the server-generated canonical proof payload. Check that user, flow, key and operation remain bound through consumption, including if a pending key changes. Review storage encoding and legacy re-enrollment.
- **Legacy data check:** are any existing `trusted_devices`, `auth_sessions`, or `email_challenges` rows present? No data-backfill or trust conversion is included.
- **Flow and enrollment restart:** how an expired 15-minute flow can reauthenticate/reconcile pending onboarding without granting re-use of a consumed provider challenge; enrollment lifetime is bounded by the active flow.
- **OTP resend lifecycle:** enforce one active challenge per user/purpose (and correct flow) with serialized service logic; the V1 table does not have a unique active-challenge index.
- **Sign-out confirmation.** D10.25 permits a verified current refresh credential after access expiry. Verify that the backend can commit revocation of both its session and current-device trust and confirm the outcome.
- **Cleanup/retention:** challenge expiry does not mean immediate deletion; preserve consumed digests long enough to meet the replay policy, audit/incident needs, and operational constraints; choose retention explicitly before implementing jobs.
- **Detailed API contract:** OpenAPI 3.1 file D10.20 is a review candidate, not final approved wire format.

## 4. Validation plan (before application to real database)

| Check | Required validation |
|---|---|
| Static | Parse SQL; count 5 creates/3 alters; verify table and column targets, FK parent keys, unique names and no V1 modifications |
| Migration | Apply original V1 followed by candidate V2 to fresh **disposable MySQL 8.4** using Flyway/Testcontainers |
| Existing-data | Seed V1 legacy trusted-device/session/OTP rows, migrate, confirm rows preserved and not silently trusted |
| FK | Reject cross-user flow/OTP/verified device association and cross-flow enrollment/challenge association |
| Atomicity | Repeat/parallel provider challenge consume, OTP consume, device proof, refresh rotation, session revoke |
| Expiry | Provider challenge 5 min, signature challenge 2 min, OTP 10 min, flow JWT 15 min, access JWT 20 min, refresh session 30-day sliding inactivity |
| Security | Reject invalid JWK, wrong ECDSA signature, stale/disabled account, recognized replay at any delay (including immediate duplicates), successor use after family revocation, unauthorized linking |
| Operational | Confirm application DB grants, Flyway version and `validate` state; staging backup/restore and controlled rollout |

**MySQL DDL caution:** MySQL DDL often commits implicitly. If a multi-statement Flyway migration fails mid-run, partial DDL may remain and require explicit cleanup. Therefore disposable-DB validation is mandatory; no direct production or existing-local application as first run.

## 5. Scope/status outcome

### Static validation performed in this review

- **PASS:** 8 DDL statements: 5 `CREATE TABLE`, 3 `ALTER TABLE`.
- **PASS:** All 10 declared foreign-key parent tables exist in V1 or V2; ordered referenced key columns have a primary/unique index in the reviewed source text.
- **PASS:** 8 `CHECK` constraints; names are not duplicated within the candidate.
- **PASS:** No `INSERT`, `UPDATE`, `DELETE`, `DROP`, or `TRUNCATE` statements.
- **PASS:** Original V1 file SHA-256 observed for this review: `4353a545701fdae3aed8c669052b2f3d965417de8b40544548386ece5968607a` (this is a file hash, **not** the Flyway schema-history checksum).
- **NOT RUN:** MySQL 8.4 parser / DDL execution, Flyway migration, constraints tested against real data, or concurrent API operations. The static checks do **not** prove executable correctness.

### References

- MySQL 8.4 foreign key constraints: https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html
- MySQL 8.4 immediate foreign key checks: https://dev.mysql.com/doc/refman/8.4/en/ansi-diff-foreign-keys.html
- Flyway migration transaction handling: https://documentation.red-gate.com/fd/schema-model-273973399.html

**D10.23 proposal only.** The accompanying SQL is structurally reviewed but **not MySQL-executed** here. Do not rename or move to `src/main/resources/db/migration` until explicit approval after disposable MySQL 8.4 tests. No D10.20 final OpenAPI approval is implied. No FR, Stage 4 UX, or 14-route MVP scope changes.
