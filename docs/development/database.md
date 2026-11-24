# Database setup

Use Java 21 and MySQL 8.4. `application.yaml` supplies the shared JDBC URL and database settings. A profile changes only the settings that differ for its environment. Hibernate uses `validate` and Flyway clean is disabled.

## Local profile in IntelliJ

1. Start the Windows MySQL service on port `3308`. In the ignored `.env.local`, set `DB_USERNAME` and `DB_PASSWORD` for an existing MySQL account. The file targets `localhost:3308`, names the database `unispace`, and enables `DB_AUTO_CREATE`.
2. In this workspace, select **Unispace - local** in IntelliJ. This configuration lives in the private `.idea/workspace.xml`. On a fresh checkout, create a Spring Boot configuration with active profile `local`, main class `com.unispace.UnispaceBackendApplication`, and `$PROJECT_DIR$/.env.local` as its environment file. IntelliJ passes those values to the process. [IntelliJ Spring Boot configuration](https://www.jetbrains.com/help/idea/run-debug-configuration-spring-boot.html)
3. Use Java 21. This workspace targets the existing `unispace` database on `localhost:3308`. With `DB_AUTO_CREATE=true`, Connector/J can create a missing database if the account has the necessary privilege. [Connector/J connection properties](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-connection.html)

The local configuration reads the existing `DB_MIGRATIONS_ENABLED` value in `.env.local`. With `true`, startup applies any pending Flyway migrations before Hibernate validates mapped entities. Use that setting only for explicitly approved migrations against the target database. Use `false` for startup without migrations.

The local profile defaults to `DB_SSL_MODE=REQUIRED`, which uses the local MySQL server's TLS without requiring its development certificate to match the host.

## Docker profile

Fill in the ignored `.env.docker` with an application user other than `root` and separate application and root passwords. On first initialization of its named volume, the MySQL image creates the database specified by `MYSQL_DATABASE`, which Compose takes from `DB_NAME` in `.env.docker`. The app container activates the Spring `docker` profile and uses `mysql:3306` with JDBC database creation disabled. Its Flyway setting comes from `DB_MIGRATIONS_ENABLED` in the Compose environment. Changing the file later does not change databases, accounts, or passwords already stored in that volume.

In IntelliJ, open **Run → Edit Configurations → UniSpace — Docker**. In **Server**, select your local Docker Desktop connection. If the configuration is missing, use **Add New Configuration → Docker → Docker Compose**, name it **UniSpace — Docker**, and select that server. Set **Compose files** to `$PROJECT_DIR$/compose.yaml` and leave **Services** empty to include both services. Under **Modify options**, set **Environment variables files** to `$PROJECT_DIR$/.env.docker` and **Build** to **Always**. Leave **Remove volumes on down** off. For a shareable manual configuration, enable **Store as project file** and save it under `.run`. [IntelliJ Docker Compose configuration](https://www.jetbrains.com/help/idea/docker-compose-run-configuration.html)

Compose requires a nonempty `MYSQL_ROOT_PASSWORD` during interpolation, including when a named volume already exists. The B2 inspection's configuration check failed because this value was empty in its environment. Supply the configured value privately when running Compose. Check interpolation without starting services with the following command.

```powershell
docker compose --env-file .env.docker config --quiet
```

When startup and any pending migrations on the named volume are authorized, use the following command.

```powershell
docker compose --env-file .env.docker up --build
```

Compose publishes only the application at `127.0.0.1:8080`. Shell variables with matching names take precedence over values in the Compose environment file. [Docker Compose interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)

## Test profile

With Java 21 and Docker available, select **UniSpace — Test** in IntelliJ. This JUnit configuration runs the `com.unispace` package in the `unispace-backend` module using VM options `-Dspring.profiles.active=test -Dunispace.mysql.tests=true`. From Maven, the test class activates `test` directly:

```powershell
.\mvnw.cmd test "-Dunispace.mysql.tests=true"
```

The test class supplies the disposable container address and credentials to both the datasource and Flyway. The suite checks all 18 tables, primary and foreign keys, unique indexes, and selected integrity rules. Hibernate validates a test-only mapping covering unsigned ID, JSON and UTC microsecond timestamp types. The default Maven `test` invocation compiles tests and skips the opt-in Docker-backed suite.

### Verification on 23 November 2026

The original 10 tests passed on a disposable container. After adding nine integrity cases, the full suite passed on another fresh MySQL 8.4 container with **19 executed, zero failures, zero errors, and zero skipped**. Flyway applied and validated V1. The run used the container's dynamically mapped port and database `test`.

The added cases cover source-side switch history, snapshot mismatches, owner transfer and last-member departure, restore-channel references, active filenames across channels, audit attribution, current refresh-hash uniqueness, provisional identity uniqueness, and notification/outbox deduplication. The switch test exercises both the FK rejection while the source reference is live and successful deletion after detachment. These tests exercise SQL constraints and transaction steps directly. Service concurrency, authorization and recovery remain open in the gates below.

## Production profile template

**UniSpace — Production** selects `prod` and has no attached environment file or credentials. For a future deployment, activate `prod` and supply `DB_HOST`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD` through the deployment platform. Set `DB_PORT` if the managed database uses a port other than `3306`. The profile requires verified TLS and uses an existing database. Provision the database CA in the Java truststore if necessary.

Production Flyway uses `DB_PROD_MIGRATIONS_ENABLED`, which defaults to `false`. Set it to `true` only for an explicitly approved production migration.

## B2 migration review

The read-only B2 inspection of local MySQL 8.4.13 found a successful Flyway V1 record with checksum `-1815709415`, matching `V1__initial_schema.sql`. The 18 InnoDB table names and column names matched V1. The live database had 18 primary keys, 16 unique constraints, 29 foreign keys (28 restrict, one cascade), and six enforced checks. The cascade applies to an upload session's parts. Production acceptance still requires the B2 physical-integrity decisions below.

| Gate | V1 behavior and remaining decisions |
| --- | --- |
| DB-G01 | The composite owner FK requires the owner to be a member of that channel. Future services must lock the channel, enforce six seats, and commit admission, transfer or departure with the final owner set consistently. Verify concurrent approval, switches, removals and last-member departure through those services. |
| DB-G02 | V1 retains target/source snapshots and uses nullable live FKs. A cancelled request can still block deletion until its live reference is detached. Future services must serialize eligibility checks, cancellation, detachment, audit and notifications. |
| DB-G03 | A file marked `DELETED` may detach from its channel and clear its restore reference. The tombstone can retain its unique storage key for retry and reconciliation. Define when permanent deletion and detachment commit, how R2 retry is tracked, and how unfinished uploads are settled during channel deletion. |
| DB-G04 | The restore FK preserves the last-channel reference and the active-name key prevents overwrites. Future restoration must recheck authorization and destination validity and allocate a numbered suffix on collision. |
| DB-G05 | User FKs restrict deletes to preserve attribution and audit. Privacy review still needs to define disable, anonymization, and hard deletion. |
| DB-G06 | A unique current refresh hash is present. Replay history, rotation, and revocation remain B3 work. |
| DB-G07 | Composite FKs require any linked device to belong to the session user. Upload sessions retain B2's proposed required device link. B3 must confirm whether uploads always have a trusted-device record and where the per-device transfer limit is enforced. |
| DB-G08 | V1 targets MySQL 8.4, uses accent-sensitive/case-insensitive name comparisons and binary comparisons for opaque identifiers. Application normalization still handles trimming and canonical forms. Hibernate uses direct JDBC Java time mappings so reading a UTC `DATETIME(6)` preserves the stored wall-clock value. Confirm the signed Java `Long` range and validate each production entity as it is added. |
| DB-G09 | Expiry timestamps, outbox retry/lease columns and deduplication keys are present. Retention workers, optional push retry handling and R2 reconciliation remain future implementation. |
| DB-G10 | V1 seeds no accounts or policies. For FR-004 the ACTIVE check requires a verification timestamp. Completed registration also needs validation before activation. Review provisioning, provisional identifier reservation, pending role requests, state validation and query plans. |

### Existing database and new test databases

**Existing installation.** Keep the applied V1 file intact. Propose any additional DDL as a new versioned migration and obtain approval for its target. The current local and Docker env files enable migrations, so starting either application can apply a pending migration to persistent storage.

**Disposable test database.** The opt-in suite creates a fresh MySQL 8.4 container, applies V1 through Flyway, and validates the resulting schema. Use this path to repeat the initial migration. For a future approved V2, also test upgrading a disposable V1 database with representative data.

MySQL commits DDL statement by statement. If a migration is interrupted, inspect the schema and Flyway history before choosing a recovery action. Preserve the existing database and Compose volume.
