# UniSpace Backend

UniSpace is a university mobile application with a Java 21, Spring Boot 4.2, feature-based modular monolith.

The current implementation provides Maven, Spring Data JPA, Flyway, MySQL 8.4, Docker Compose, and JUnit/Testcontainers infrastructure.

The Google-first backend supports sign-in, student onboarding, university-email verification, trusted-device enrollment and proof, session refresh, and current-device sign-out. B3-I09 verified all 12 backend journeys with controlled fixtures. Full MVP authentication and production acceptance remain open. Private R2 storage and other backend modules remain pending.

## 1. Requirements and Architecture

- Follow approved specifications in `docs/requirements/`.
- Read User Flows v1.0 together with Addendum v1.1.
- Preserve all 167 Functional Requirements: 150 MVP and 17 post-MVP.
- Preserve FR IDs, approved behavior, and MVP scope.
- Use B0-01 v0.2.1 for requirements traceability.
- B0 decisions and mappings are approved; the B0 handoff records consolidated sign-off as pending.
- Follow the approved B1.1–B1.5 architecture decisions, the B2 database design, and current B3 authentication decisions in `docs/architecture/`. B1.6 consolidated review and final sign-off remain pending.
- Preserve any outstanding B2 physical-integrity verification requirements.
- Treat the Backend Engineering Starter Pack as a historical draft.
- Do not treat review-only OpenAPI contracts as final approved API specifications.
- Identify documentation conflicts before implementing changes.

## 2. Current Development Status

Verified checkpoint: November 25, 2026.

- B3-I01 — Repository audit: Completed.
- B3-I02 — Authentication contract synchronization: Completed.
- B3-I03 — Flyway V2 verification: Passed.
- B3-I03A — Runtime V2 migration promotion: Passed.
- B3-I03B — Database test isolation: Passed.
- B3-I04A — Security implementation inspection and planning: Completed, read-only.
- B3-I04 — Spring Security and JWT foundation: Implemented and verified on November 24, 2026. Deployment key provisioning and Compose key mounts remain pending separate approval.
- B3-I04B/C — Security audit and corrections completed November 24, 2026. Verification now checks compact JWT encoding, device key usage, email verification time, and the time of provider challenge consumption.
- B3-I05 — Google challenge issuance and provider exchange implemented and verified with controlled fixtures on November 24, 2026. Live Google Cloud and mobile client integration remain pending.
- B3-I06 — Onboarding and university-email OTP implemented and verified with a controlled sender on November 24, 2026. Successful registration verification creates eligibility for initial device enrollment. Live mail delivery requires configuration and verification.
- B3-I07 — Trusted-device enrollment, new-device OTP, P-256 possession proof, and initial session issuance implemented and verified on November 25, 2026. See the [device possession protocol](docs/architecture/B3/Device_Possession_Protocol_v1.md) for the byte format and test vector.
- B3-I08 — Session refresh rotation, strict consumed-token replay revocation, and current-device sign-out implemented and verified on November 25, 2026. Clients use either the bearer or refresh-body sign-out form. Mixed-credential precedence remains open.
- B3-I09 — Authentication integration and security verification completed November 25, 2026. The Google-first implementation is ready for downstream backend development. Replaying the oldest refresh credential after two or six rotations revokes only its family, including under a competing rotation. The signed Google subject must be a string. [Journey evidence, security audit and release requirements](docs/architecture/B3/B3-I09_Authentication_Verification.md)

Latest database regression results:

- Original V1 regression tests: 19 passed.
- V2 migration tests: 5 passed.
- Database regression subtotal: 24 passed, 0 failed, 0 errors, 0 skipped.

The same B3-I04 Maven run passed 20 JWT tests, 3 signing-key tests, and 8 HTTP and authoritative-state integration tests. Combined result: 55 executed, 55 passed, 0 failed, 0 errors, 0 skipped.

The subsequent B3-I04B/C run retained all 55 tests and added four security regressions. The opt-in Maven run on disposable MySQL 8.4.11 passed all 59 tests (19 V1, 5 V2, 21 JWT, 3 signing-key, 11 HTTP and authoritative-state). Failures, errors, and skips were all zero.

The B3-I05 opt-in Maven run passed all 59 existing tests plus 15 Google token-verifier tests and 9 Google HTTP and transaction tests. Combined result is 83 executed, 83 passed, 0 failed, 0 errors, and 0 skipped.

The B3-I06 opt-in Maven run passed all 83 existing tests and 9 new onboarding tests. Combined result is 92 executed, 92 passed, 0 failed, 0 errors, and 0 skipped.

The B3-I07 opt-in Maven run passed all 92 existing tests, 2 cryptographic tests, and 8 device integration tests. Combined result is 102 executed, 102 passed, 0 failed, 0 errors, and 0 skipped.

The B3-I08 opt-in Maven run passed the 102 existing tests and 11 session lifecycle integration tests. Combined result is 113 executed, 113 passed, 0 failed, 0 errors, and 0 skipped.

The B3-I09 run on disposable MySQL 8.4 retained those 113 tests and added four HTTP journey cases and one Google verifier regression. Maven executed 118 tests. All 118 passed, with 0 failures, 0 errors and 0 skipped.

Do not claim tests have been rerun unless they were actually executed.

## 3. Configuration Protection — CRITICAL

Inspect and preserve the existing Spring Boot profiles, environment files, IntelliJ run configurations, and Docker Compose configuration.

### Existing IntelliJ configurations

| Configuration | Profile | Environment |
|---|---|---|
| `Unispace - local` | local | `.env.local` |
| `UniSpace - Docker` | docker | `.env.docker` supplied to Compose |
| `UniSpace - Production` | prod | External environment configuration |
| `UniSpace - Test` | test | Testcontainers |
| `Dockerfile build` | None | Image build only |

Use the actual saved run configurations and verify their environment-variable bindings.

### Database connectivity

- Local development: `localhost:3308`.
- Docker internal connection: `mysql:3306`.
- Tests: Disposable MySQL 8.4 through Testcontainers, using dynamically assigned host ports.
- Production: Externally configured database endpoint; do not assume a host or port beyond existing settings.

See the [database setup guide](docs/development/database.md) for profile and connection details.

`.env.test` is not required because database tests obtain their connections from Testcontainers.

`.env.prod` does not currently exist and must not be invented or required for development. Production secrets and settings may be supplied by the deployment environment.

### Protected files and settings

Do not modify without explicit approval:

- `.env` and `.env.*`
- `application.yml`, `application.yaml`, and profile-specific configuration files
- `compose.yaml`
- Dockerfiles and port mappings
- IntelliJ run configurations
- Database connection settings
- Existing environment-variable names
- Security settings and credentials
- Flyway runtime configuration
- Existing Maven configuration outside the approved implementation scope

Never replace existing values with examples or generated defaults.

Never change `localhost:3308` to `localhost:3306`.

Never assume `.env` files are automatically loaded by Spring Boot. Inspect the established IntelliJ or Compose mechanism.

Do not print or expose secrets.

## 4. Database and Flyway Safety

Existing migrations:

- `V1__initial_schema.sql`
- `V2__authentication_security.sql`

Both migrations have been verified using disposable MySQL 8.4.

V1 is recorded as applied to the existing local database. Each persistent migration requires explicit approval for the target database.

- Preserve V1 and V2 SQL bytes and verified checksums.
- Use new forward-only Flyway versions for approved future schema changes.
- Do not reset persistent databases or Docker volumes.
- Never execute Flyway clean against persistent databases.
- Never access or migrate `localhost:3308` without explicit authorization.
- The current `.env.local` and `.env.docker` files set `DB_MIGRATIONS_ENABLED=true`. Starting the local application or Compose stack with those settings can apply pending migrations to its persistent database. Obtain approval for the specific target first.
- Runtime V2 promotion does not constitute authorization to apply V2 to a persistent database.

### Database testing

Use:

`.\mvnw.cmd -B -ntp test "-Dunispace.mysql.tests=true"`

The original 19 V1 tests use a test-class-specific Flyway target of version 1.

The five V2 tests independently verify the V1-to-V2 migration.

Preserve this isolation and all existing tests.

Report executed, passed, failed, errored, and skipped counts.

## 5. Authentication Security Rules

Follow the latest approved B3 authentication decisions.

Core requirements:

- Access JWT: RS256, 3072-bit RSA key, 20-minute expiration.
- Restricted authentication-flow JWT: RS256, 15-minute expiration.
- Separate access and flow audiences and purposes.
- Access authorization requires authoritative server-side account, session, device, and current-role checks.
- Refresh tokens use rotation and a 30-day sliding inactivity window.
- Every recognized consumed refresh-token reuse revokes the affected session/token family immediately.
- University email OTP: 10-minute expiration, 60-second resend cooldown, five failed attempts invalidate.
- Trusted-device proof: P-256/SHA-256 with two-minute single-use challenges.
- Never merge provider identities based only on matching email addresses.
- Follow approved session revocation and sign-out policies.

Do not introduce unapproved endpoints, authentication shortcuts, or insecure fallback behavior.

Never commit private keys, provider secrets, OTPs, passwords, or refresh tokens.

Use separate test-only signing keys for automated tests.

## 6. Development Workflow

1. Read `AGENTS.md` and relevant approved documentation.
2. Inspect the actual repository and Git status.
3. Inspect existing implementation and configuration before proposing changes.
4. Identify required modifications and explain their purpose.
5. Propose exact file changes and significant design decisions.
6. Wait for explicit approval before significant or protected changes.
7. Implement only the approved phase.
8. Follow TDD and run relevant tests.
9. Preserve existing test coverage, database migrations, and working configurations.
10. Report changed files, actual test results, deviations, and remaining blockers.

Do not overwrite unrelated or uncommitted work.

Never silently change approved architecture or business rules.

Do not commit or push without explicit authorization.

## 7. Next Implementation Boundary

**B3-I09 verification is complete. B4 Administration is ready to begin upon separate authorization.**

Full MVP authentication still requires Apple sign-in and both `GET` and `POST /api/v1/me/linked-providers` for FR-167. Open security work includes public challenge rate limiting, authentication audit-event capture and retention evidence, and refresh HMAC key rotation that preserves replay detection.

Production acceptance requires final API contract approval, deployment keys and configuration, live Google and SMTP checks, and Android/iOS verification of device signing, secure storage and Flutter session handling. See the [B3-I09 release checklist](docs/architecture/B3/B3-I09_Authentication_Verification.md).

Do not modify protected environment configuration without separate approval.

Stop after the authorized phase and report implementation results.

---

**Core rule: Inspect first. Preserve working configurations. Implement only approved changes. Verify everything.**
