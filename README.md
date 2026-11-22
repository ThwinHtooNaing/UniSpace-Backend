# UniSpace Backend

UniSpace is a university-focused mobile application. Its backend is a Java 21 Spring Boot application organized as a feature-based modular monolith. The current implementation covers database infrastructure. Product requirements and backend traceability are in [requirements](docs/requirements/) and [architecture/B0](docs/architecture/B0/).

## Build

Install JDK 21 and run the Maven wrapper from the repository root. Packaging can run without a database connection.

| Platform | Command |
| --- | --- |
| Windows PowerShell | `.\mvnw.cmd package` |
| Linux or macOS | `sh ./mvnw package` |

## Run locally

1. Start MySQL 8.4 with TLS enabled. Use an existing development database and a dedicated account with access to it.
2. In IntelliJ IDEA, create a run configuration for `com.unispace.UnispaceBackendApplication` with JDK 21. Set `DB_USERNAME` and `DB_PASSWORD` in its environment. Set the host, port, and database name if they differ from the defaults below.
3. Run the main class. For a terminal launch, set the same variables in the shell environment and run `.\mvnw.cmd spring-boot:run` on Windows or `sh ./mvnw spring-boot:run` on Linux or macOS.

To use an environment file in IntelliJ, copy [.env.example](.env.example) to the Git-ignored `.env.local`, fill in your local settings, and select the file in the run configuration. The example uses port `3308` and TLS mode `REQUIRED`. The base configuration uses port `3306` and verifies the server identity. Keep credentials in the private file or the process environment.

## Database configuration

The shared settings come from [application.yaml](src/main/resources/application.yaml).

| Environment variable | Base default | Purpose |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL hostname |
| `DB_PORT` | `3306` | MySQL TCP port |
| `DB_NAME` | `unispace` | Database name |
| `DB_USERNAME` | Required | Application database account |
| `DB_PASSWORD` | Required | Password for that account |
| `DB_SSL_MODE` | `VERIFY_IDENTITY` | Verify the server certificate and hostname. The JVM must trust the issuing CA. |
| `DB_AUTO_CREATE` | `false` | Allow the JDBC driver to create a missing database |
| `DB_MIGRATIONS_ENABLED` | `false` | Run pending Flyway migrations at startup |

Database sessions use UTC and `REPEATABLE READ` isolation. Hibernate validates mapped entities against the schema. SQL script initialization is disabled, and Flyway clean is disabled. Applying migrations to a persistent database requires explicit approval for that target.

## Database tests

The default Maven build skips the opt-in MySQL integration suite. The suite requires Docker, the test profile, and the V1 Flyway migration. The test profile and migration are pending inclusion in the repository, so a fresh clone can package the application but cannot yet verify the schema with this suite.

Once those files are present, run the suite against a disposable Testcontainers MySQL instance.

```powershell
.\mvnw.cmd test "-Dunispace.mysql.tests=true"
```
