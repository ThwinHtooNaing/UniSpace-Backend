# UniSpace — B3-D10.24 MySQL 8.4 Migration Compatibility Review

**Status:** STATIC VALIDATION PASSED; MYSQL 8.4 EXECUTION **NOT RUN / BLOCKED BY TEST ENVIRONMENT**  
**Review date:** 24 November 2026  
**Scope:** Approved B3-D10.23 *review-only* SQL candidate applied **only to a disposable MySQL environment** when available.  
**No live/local project database altered. No migration applied.**

## 1. Exact inputs

- `V1__initial_schema.sql` — original, immutable 18-table migration from the user.
- `REVIEW_ONLY_V2__authentication_security.sql` — five-table/three-extension **review candidate**, not an approved Flyway migration.
- `UniSpace_B3_D10_24_Disposable_MySQL84_Tests.py` — Python 3.10+ test harness with no extra Python packages.
- `UniSpace_B3_D10_24_Static_Results.json` — output of the static validation actually executed.

Original V1 SHA-256: `4353a545701fdae3aed8c669052b2f3d965417de8b40544548386ece5968607a`. This is **not** Flyway's migration checksum.

## 2. Checks actually performed

| Test | Actual result |
|---|---|
| V1 unchanged against previously reviewed SHA-256 | **PASS** |
| 18 V1 CREATE TABLE statements | **PASS** |
| Exactly five new V2 tables and three allowed ALTER TABLE statements | **PASS** |
| Eight V2 DDL statements; no data writes, DROP or TRUNCATE | **PASS** |
| Ten referenced V2 foreign keys point to a parent table and primary/unique key columns | **PASS (static)** |
| Eight CHECK constraints; no duplicate named constraints across V1/V2 | **PASS (static)** |
| Executable MySQL 8.4 DDL parsing and migration | **NOT RUN** |
| Flyway `migrate`/`validate`/schema-history | **NOT RUN** |
| Positive/negative InnoDB constraint tests | **NOT RUN** |
| Concurrent refresh/OTP/session logic tests | **NOT POSSIBLE** until Spring Boot services exist |

**Why the MySQL tests were not run here:** This execution sandbox has neither the Docker executable nor a MySQL server, and it cannot resolve external package/image hosts. The Docker test attempt returned `Docker CLI not installed`. Substituting MariaDB or SQLite would **not** verify MySQL 8.4 compatibility, so neither was used.

**Accuracy boundary:** Static checks are structural checks. They do not establish that MySQL 8.4 will accept all SQL, that Flyway can migrate it, or that backend security logic is correct.

## 3. Reproducible disposable MySQL 8.4 test — Windows 11

Place the **three input files** listed above in a new temporary directory, not inside the application's `src/main/resources/db/migration` directory.

In PowerShell with Docker Desktop running:

```powershell
cd C:\path\to\temporary\unispace-d10-24-tests

docker pull mysql:8.4
py -3 .\UniSpace_B3_D10_24_Disposable_MySQL84_Tests.py --docker --json-report .\mysql84_test_results.json
```

The harness:

1. Validates V1's original SHA-256, table/constraint structure, and permitted V2 statements.
2. Creates a **randomly named disposable `mysql:8.4` container**, with `--network none`, **no published port**, **no mounted host directory**, and **no persistent volume**.
3. Creates a new `unispace_test` database inside that container, executes V1, and seeds two synthetic users, trusted-device records, sessions and an OTP challenge.
4. Executes the *review-only* V2 SQL in that same disposable container and checks that all 23 tables exist and legacy rows remain.
5. Inserts valid records for provider authentication, restricted flow, device enrollment, signature challenge, consumed-refresh history, and user-bound OTP.
6. Attempts **13 invalid operations**; verifies MySQL rejects cross-user/invalid challenge bindings, invalid enum/provider values, invalid chronology, duplicate nonces/consumed tokens, malformed JSON, and CHECK violations.
7. Removes **only the new container it created** at the end, even on test failure.

No Docker host ports are published. The harness does not know or contact the user's local `localhost:3308`, production cloud database, or GitHub repository. Its MySQL password is generated solely for the disposable container.

If the Docker test succeeds, the successful stdout and `mysql84_test_results.json` become review evidence. A failure must be investigated before any Flyway migration approval. Docker-mode tests execute SQL using the MySQL client; they are **not equivalent to running Flyway**.

## 4. Required follow-on verification before approving V2

1. **Run Docker MySQL 8.4 harness** and retain full results. The script has been compiled and statically run here, but its Docker path is **untested** due environment limitations.
2. **Run actual Flyway migration + validate** against a separate disposable MySQL 8.4 database. Stage V1 and the candidate (renamed to `V2__authentication_security.sql` **only inside the disposable test fixture**) to verify `flyway_schema_history`, checksums, repeatable deployment, and failure recovery. Do not rename/copy the review SQL into the real Spring Boot migration directory until final sign-off.
3. **Test migration against realistic legacy data**, not just the synthetic seed, and inspect existing rows before designing any conversion of legacy `trusted_devices.device_key_hash` records.
4. **Implement service-level concurrency/security tests later:** the 15-second refresh duplicate window and replay revocation, atomic email OTP consumption, nonce/challenge invalidation, signature verification, device-revocation enforcement, and expired-session sign-out.
5. **Resolve protocol decisions still open:** public JWK canonicalization/thumbprint definition, OTP flow/replacement concurrency, retention/cleanup and full B3-D10.20 OpenAPI finalization. Database constraints alone cannot prove these security controls.

## 5. Decision/status

- B3-D10.23 was approved **for disposable testing**, not for applying to the user's existing database.
- B3-D10.24 cannot be recorded as a **MySQL 8.4 or Flyway execution pass** yet.
- The approved scope remains **167 FRs, 14 mobile-facing authentication endpoints**, Google-first rollout, deferred Apple live integration, and unchanged Stage 4 UX.
- `V1__initial_schema.sql` remains immutable. The V2 file remains deliberately named `REVIEW_ONLY_...`.

**Recommended approval gate:** keep B3-D10.24 *testing incomplete* until the disposable MySQL 8.4 results and later Flyway validation are reviewed. Do not apply V2 to the existing MySQL installation at this point.
