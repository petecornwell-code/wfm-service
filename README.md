# WFM Service

Workforce management system that allocates agents to timeslots using constraint-based optimisation. Built with Spring Boot (Java 21), Timefold Solver, PostgreSQL, and React.

## Architecture

```
├── build.gradle              # Backend build (Spring Boot + Timefold + POI)
├── settings.gradle
├── src/main/java/com/wfm/   # Backend source
│   ├── model/                # JPA entities + Timefold annotations
│   ├── repository/           # Spring Data JPA repositories
│   ├── service/              # Business logic + solver lifecycle
│   ├── controller/           # REST API endpoints (/api/v1)
│   ├── dto/                  # Request/response DTOs
│   ├── config/               # Tenant filter, CORS, app config
│   ├── integration/          # BambooHR client (mock + HTTP)
│   └── solver/               # Timefold ConstraintProvider
├── src/main/resources/
│   ├── application.yml       # App configuration
│   ├── solverConfig.xml      # Timefold solver config
│   └── db/migration/         # Flyway migrations
└── frontend/                 # React frontend (Vite + TypeScript)
    ├── src/
    │   ├── pages/            # Page components (one per UI section)
    │   ├── api/client.ts     # API client with typed endpoints
    │   └── App.tsx           # Router + layout
    └── package.json
```

## Prerequisites

- **Java 21+** (JDK)
- **PostgreSQL** (local install)
- **Node.js 20+** and **npm** (for the frontend)

## Database Setup

1. Create the database and user:

```sql
CREATE USER wfm WITH PASSWORD 'wfm';
CREATE DATABASE wfm OWNER wfm;
```

Or via psql:

```bash
sudo -u postgres psql -c "CREATE USER wfm WITH PASSWORD 'wfm';"
sudo -u postgres psql -c "CREATE DATABASE wfm OWNER wfm;"
```

2. Flyway will automatically run migrations on application startup.

## Running the Backend

```bash
# From the project root
./gradlew bootRun
```

The API starts on `http://localhost:8080`. All endpoints require an `X-Tenant-ID` header (any integer for local dev, e.g. `1`).

Health check: `http://localhost:8080/actuator/health`

## Running the Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:3000` and proxies `/api` requests to `localhost:8080`.

## Configuration

Key properties in `src/main/resources/application.yml`:

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/wfm` | Database URL |
| `spring.datasource.username` | `wfm` | Database user |
| `spring.datasource.password` | `wfm` | Database password |
| `cors.allowed-origins` | `http://localhost:3000` | CORS origins |
| `bamboohr.mock` | `true` | Use mock BambooHR client |
| `solver.time-limit` | `PT5M` | Max solver duration |

Override via environment variables (e.g. `SPRING_DATASOURCE_URL`) or a local `application-local.yml`.

## IntelliJ IDEA Ultimate Setup

### 1. Import the Project

1. **File > Open** and select the `wfm-service` root directory.
2. IntelliJ will detect the Gradle build. Click **Trust Project** when prompted.
3. Wait for Gradle sync to complete (watch the progress bar in the bottom-right).

### 2. Verify JDK

1. **File > Project Structure > Project** (Cmd+; / Ctrl+Alt+Shift+S).
2. Set **SDK** to a Java 21+ JDK. If none is listed, click the dropdown and choose **Download JDK** to install one (e.g. Eclipse Temurin 21).
3. Set **Language level** to `21`.

### 3. Configure the Database (IntelliJ Database Tool)

1. Open the **Database** tool window (View > Tool Windows > Database).
2. Click **+** > **Data Source** > **PostgreSQL**.
3. Set:
   - **Host:** `localhost`
   - **Port:** `5432`
   - **Database:** `wfm`
   - **User:** `wfm`
   - **Password:** `wfm`
4. Click **Test Connection** to verify. Download the driver if prompted.
5. Click **OK**.

### 4. Run the Backend

1. Open `src/main/java/com/wfm/WfmApplication.java`.
2. Click the green **Run** gutter icon next to the `main` method, or use **Run > Run 'WfmApplication'**.
3. Alternatively, open the Gradle tool window and run `bootRun` under **Tasks > application**.
4. The app starts on port 8080. Check the Run console for startup logs.

### 5. Run the Frontend

1. Open the **Terminal** tool window (Alt+F12).
2. Run:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
3. Or configure an **npm** run configuration:
   - **Run > Edit Configurations > + > npm**
   - **Package.json:** select `frontend/package.json`
   - **Command:** `run`
   - **Scripts:** `dev`

### 6. Enable Spring Boot Features

IntelliJ Ultimate auto-detects Spring Boot. Verify:

1. **File > Project Structure > Facets** — a Spring facet should be auto-detected.
2. The **Endpoints** tool window (View > Tool Windows > Endpoints) will list all REST endpoints once the project indexes.
3. The **Spring** tool window shows beans, configurations, and wiring.

### 7. JPA / Database Integration

1. **File > Project Structure > Facets** — verify a JPA facet is detected.
2. Assign the PostgreSQL data source you configured in step 3 to the JPA facet — this enables:
   - JPQL/HQL auto-completion in `@Query` annotations.
   - Entity-to-table mapping validation.
   - Navigation from entity fields to database columns.

### 8. Running Tests

- Right-click the `src/test/java` directory and select **Run 'All Tests'**.
- Or run `./gradlew test` from the terminal.
- Tests use an H2 in-memory database (configured via `testRuntimeOnly 'com.h2database:h2'`).

### Tips

- **Hot reload:** Add the Spring Boot DevTools dependency and enable "Build project automatically" (Settings > Build > Compiler) for automatic restarts on code changes.
- **HTTP Client:** IntelliJ's built-in HTTP client (`.http` files) is convenient for testing API endpoints. Create a file like `requests.http` with:
  ```http
  ### List desks
  GET http://localhost:8080/api/v1/desks
  X-Tenant-ID: 1
  ```
- **Lombok (if added):** Enable annotation processing: Settings > Build > Compiler > Annotation Processors > Enable.
