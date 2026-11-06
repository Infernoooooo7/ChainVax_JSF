# ChainVax: Cold-Chain Custody and Excursion Ledger for Vaccine Supply

Academic demo for **JAVA and Spring Framework Lab (CS3603-1)**, Department of Computer Science and Engineering (V Semester), NMAMIT, AY 2026-27.

| USN | Name |
|-----|------|
| NNM24CS170 | Pavan MP |
| NNM24CS168 | P V Ananthakrishna Karanth |
| NNM24CS175 | Ponapula Snehith Shree Pranav |
| NNM24CS158 | Nikith Rao S |

> **Demo data only.** All sites, batches, loggers and temperature readings are simulated. The `DEMO-FAST` policy shrinks hours into seconds so the lifecycle can be shown live. Neither policy is medical guidance. Automatically condemning a batch at an alarm threshold is a ChainVax design choice, not a WHO rule (see [docs/RESEARCH.md](docs/RESEARCH.md)).

What it does:

- **Ingests readings.** Temperature readings come in through the same API a real data logger would use; a built-in simulator stands in for the hardware.
- **Tracks excursions and status.** ChainVax tracks one excursion window per batch and moves each batch **VIABLE → AT_RISK → CONDEMNED** according to its policy.
- **Withdraws condemned stock.** Condemned stock is removed from issuable stock automatically.
- **Chains custody hand-overs.** Every custody hand-over (district store → van → health centre) is appended to a ledger that stores `SHA-256(previous_hash + payload)`. The chain can be verified through `GET /api/ledger/verify`.
- **Enforces four roles.** Spring Security with JWT enforces storekeeper, transporter, district officer and auditor permissions.
- **Audits officer overrides.** FEFO overrides, AT_RISK releases and policy changes are each recorded against the officer who made them.

---

## 1. Quick start (Windows)

You need only a **JDK 17 or newer** on `PATH` (this was tested with JDK 25). No PostgreSQL, Redis, Docker or Node.js install is required for the default mode. Docker mode (below) adds real PostgreSQL and Redis containers.

1. Double-click **`start-demo.cmd`**, or run it from a terminal.
   - The first run builds `backend\target\chainvax.jar` with the bundled Maven Wrapper. Internet access is needed once, for the Maven downloads.
   - After that, it starts in about 20–40 seconds.
   - `start-demo.cmd rebuild` forces a rebuild after code changes.
2. Open **http://localhost:8080** and click one of the demo users.
3. The API docs are at **http://localhost:8080/swagger-ui.html**, with the OpenAPI JSON at `/v3/api-docs`. To use them, call `POST /api/auth/login`, then click **Authorize** and paste the `accessToken`.
4. Stop with **Ctrl+C** in the terminal window.

Every start creates a **fresh in-process PostgreSQL 17 database** and re-seeds the demo data, so restarting resets the scenario. Set `CHAINVAX_PORT=9090` to use a different port.

### Docker mode (PostgreSQL + Redis in containers)

With Docker Desktop running, the same launcher can use the brief's full stack: PostgreSQL 17 and Redis 7 from `docker-compose.yml`.

| Command | What happens |
|---------|--------------|
| `start-demo.cmd docker` | Starts the containers (waits until healthy), then the app with the `postgres,redis` profiles. Data is **kept** between runs; the seeder skips if data exists. |
| `start-demo.cmd docker-reset` | Same, but wipes the Docker database first, so you get fresh demo data. |
| `start-demo.cmd rebuild docker` | Rebuild the jar, then Docker mode. |
| `docker compose down` (in the project folder) | Stop the containers. Add `-v` to also delete the data. |

The dashboard footer and `GET /api/system/info` show which database and cache are in use (`RedisCacheManager`, `embeddedDatabase: false`).

Manual equivalent:

```bat
cd backend
mvnw.cmd -B package            :: builds target\chainvax.jar and runs the test suite (add -DskipTests to skip)
java -jar target\chainvax.jar
```

To work on the UI with hot reload, keep the backend running and use the Vite dev server:

```bat
cd frontend
npm install
npm run dev                     :: http://localhost:5173, proxies /api, /v3 and /swagger-ui to :8080
npm run build                   :: writes the production UI into backend\src\main\resources\static
```

## 2. Demo users

All demo accounts use the password **`demo1234`**. The login page offers one-click sign-in, and the sidebar lets you switch users.

| Username | Role | Scope | Can do |
|----------|------|-------|--------|
| `officer` | District officer | District-wide | Everything: transfers anywhere, FEFO overrides, AT_RISK release, policy changes, digest run, tamper demo |
| `store.dvs` | Storekeeper | DVS-UDP (district store) | Transfer and issue stock held **at their own site** |
| `driver.van01` | Transporter | VAN-01 (cold-chain van) | Transfer stock carried in their van; cannot issue stock |
| `store.phc` | Storekeeper | PHC-NTE (health centre) | Transfer and issue stock at their own site |
| `auditor` | Auditor | Read-only | View everything, verify the ledger; every write returns 403 |

Each logger has its own device secret (`demo-logger-secret`) and exchanges it at `POST /api/auth/device-token` for a JWT with role `LOGGER`. Only that token can post readings.

Seed data (every start): 3 sites, 3 loggers, 7 batches (810 vials) and 13 custody-ledger entries.

| Site | Batches |
|------|---------|
| District Vaccine Store, Udupi (`DVS-UDP`) | CVX-PENTA-001, CVX-PENTA-002, CVX-MR-002 |
| Van KA-20-CV-0101 (`VAN-01`) | CVX-MR-001, CVX-TD-001 |
| PHC Nitte (`PHC-NTE`) | CVX-BOPV-001, CVX-PCV-001 |

Batches use the `DEMO-FAST` policy, except **CVX-TD-001**, which uses the `PQS-E006-T1` reference policy.

## 3. Presentation script (about 10 minutes)

This flow was rehearsed against a fresh start. Sign in as **officer**.

1. **Dashboard.** Three sites (district store → van → health centre), 7 batches all VIABLE, and the custody ledger shown as **Valid**. Point out the *Simulated logger data* and *Demo thresholds* badges.
2. **Telemetry & simulator.** Explain that each logger gets a device JWT and posts to `POST /api/telemetry/readings`. On **LOG-VAN-01**, click **Heat excursion**.
   - The ingestion response shows CVX-MR-001 and CVX-TD-001 going **VIABLE → AT RISK**.
   - Countdowns appear: DEMO-FAST condemns after **1m 30s**, while the PQS reference policy needs **10 h**.
3. **Stock & FEFO issue** (while the 90 s run). Choose site **DVS-UDP** and product **Pentavalent**.
   - **Issue vials:** 10 vials come from **CVX-PENTA-002** (expiry 2027-01-31) before CVX-PENTA-001 (FEFO).
   - **Officer override:** choose CVX-PENTA-001 and keep the reason. This creates *Override recorded (#1)*.
4. **Dashboard or Telemetry.** **CVX-MR-001 is CONDEMNED**. The reason names the sustained duration, the van, custodian `driver.van01` and the logger evidence. Then click **Normal 4-6 °C** on LOG-VAN-01 to end the excursion.
5. **Stock**, site **VAN-01**, product **Measles-Rubella**. Issuable stock is 0 and CVX-MR-001 is listed under *Withdrawn - never issuable*.
   - *Issue vials* is refused with `409 INSUFFICIENT_ISSUABLE_STOCK`.
   - An override with CVX-MR-001 is refused with `409 BATCH_CONDEMNED`.
6. **Batches.**
   - Open **CVX-TD-001** (AT RISK) and use **Release batch**, keeping the VVM reason. It returns to VIABLE, recorded against the officer.
   - Open **CVX-MR-001** to show the lifecycle timeline, the excursion window, its custody chain and the Envers revision history (who changed it).
7. **Custody ledger.**
   - Transfer **CVX-TD-001 → PHC-NTE → store.phc**. Entry **#14** is appended, and its `previous_hash` equals the hash of #13.
   - In the inspector, *Recomputed in this browser (Web Crypto) … matches*. Then click **Verify whole chain**: valid.
   - Try **CVX-MR-001 → PHC-NTE**. It is refused with `409 CONDEMNED_RETURN_ONLY`, because condemned stock can only go back to a district store.
   - **Tamper** entry #3, which changes the `vials` column with raw SQL. The chain shows **BROKEN** with `#3 HASH_MISMATCH`. Click **Restore**, and it is valid again.
8. **Audit trail.**
   - *Officer overrides* shows FEFO_OVERRIDE and STATUS_RELEASE, each with the officer's name and reason.
   - *Lifecycle events* and *Expiry / alert digests* come from the `@Scheduled` worker every 5 minutes; **Run digest now** triggers one on demand.
   - *Excursion policies* shows the DEMO-FAST label and the PQS basis.
9. **Role access check.** Switch user in the sidebar and click **Run real API calls**:
   - `auditor`: all writes are **403**.
   - `driver.van01`: may transfer, but issuing stock is 403.
   - `store.dvs`: a transfer of the van's batch is `403 FORBIDDEN_SCOPE` (own site only).
10. **Swagger UI.** Briefly show `/swagger-ui.html`.

Optional (about 90 s): on **LOG-PHC-NTE**, click **Heat excursion** and then **Logger silent**. No further readings arrive, but the `@Scheduled` excursion worker still condemns the PHC batches after 90 s. Their Envers history shows `system:excursion-worker`. This shows that a silent logger cannot hide an excursion.

## 4. How it works

```
React UI (Vite + Tailwind, served from Spring Boot /static)
   │  JWT (Bearer)                                     simulated loggers ──device JWT──┐
   ▼                                                                                   ▼
Spring Boot 4.1 REST API ── Spring Security (roles + own-site scope checks) ── POST /api/telemetry/readings
   │
   ├─ telemetry:  readings (append-only) → excursion windows per batch → ExcursionRules(policy)
   │              → BatchLifecycleService (VIABLE→AT_RISK→CONDEMNED, escalate-only; officer release audited)
   ├─ custody:    CustodyService rules → LedgerService.append (row-locked head, SHA-256 chain) / verify
   ├─ stock:      FEFO allocator (@Transactional + @Version on batches) · officer override → override_audit
   ├─ audit:      override_audit, batch_status_event, Hibernate Envers revisions (actor = JWT user/device/system)
   ├─ workers:    @Scheduled ExcursionWorker (10 s, wall-clock exposure) · DigestWorker (5 min, in-app inbox)
   └─ cache:      Spring Cache "unitState" + "dashboard" (in-memory; Redis with the redis profile), evicted after commit
   │
PostgreSQL 17 (embedded via zonky by default) · schema owned by Flyway (V1 schema, V2 reference data)
```

The key rules:

- **Excursion exposure.**
  - When a reading is ingested, exposure is measured with the readings' own timestamps.
  - The scheduled worker measures it against the wall clock. An open window only closes when an in-range reading arrives, so a logger going silent cannot hide an excursion.
- **Status changes.**
  - Status only escalates. The single exception is an officer releasing a batch from AT_RISK back to VIABLE. That is refused while the excursion is still open, and it is audited.
  - CONDEMNED is final, and the batch is never issuable again, not even by override.
- **Custody rules.**
  - A receiving custodian who is assigned to a site must be assigned to the destination site, and auditors can never take custody.
  - A storekeeper or transporter may only hand over stock held at their own site.
  - Condemned stock can only be returned to a district store.
- **Ledger hash.** `hash = SHA-256(previous_hash + canonical JSON payload)`, starting from a genesis value of 64 zeros. Appends lock the single-row ledger head. The verifier rebuilds each payload from the stored columns, so editing any column is detected, not just editing the JSON text. It reports these problems:
  - `HASH_MISMATCH`: an entry's contents were changed.
  - `BROKEN_LINK`: an entry does not point to the hash of the one before it.
  - `SEQUENCE_GAP`: entries are missing from the middle.
  - `HEAD_MISMATCH`: entries were removed from the end.

Main endpoints are listed below; all 35 paths are in Swagger.

| Method & path | Purpose | Roles |
|---------------|---------|-------|
| `POST /api/auth/login`, `POST /api/auth/device-token` | Issue JWTs for staff and for loggers | public |
| `POST /api/telemetry/readings` | Logger ingestion (batch of readings) | LOGGER (device token) |
| `GET /api/batches`, `GET /api/batches/{id}` | Unit state, timeline, excursions, custody | all staff |
| `POST /api/batches/{id}/release` | AT_RISK → VIABLE after inspection | district officer |
| `POST /api/custody/transfers` | Record hand-over and append ledger entry | storekeeper, transporter (own site), officer |
| `GET /api/ledger`, `GET /api/ledger/verify` | Ledger and full-chain verification | all staff |
| `GET /api/stock/issuable`, `POST /api/stock/issue` | FEFO view and issue (optional officer override) | storekeeper (own site), officer |
| `PUT /api/policies/{id}` | Change thresholds (versioned, audited) | district officer |
| `GET /api/audit/overrides`, `/status-events`, `/batches/{id}/revisions` | Audit trail and Envers history | all staff |
| `GET /api/digests`, `POST /api/digests/run` | Expiry and alert digests | all staff / officer |
| `POST /api/simulator/devices/{code}/mode` | Simulator scenario (NORMAL/HEAT/COLD/SILENT) | operators |
| `POST /api/demo/ledger/{seq}/tamper` and `/restore` | Demo-only rogue-SQL simulation | district officer |

## 5. Requirements checklist

Each item is in one of three states: **Done and verified** (exercised and checked), **Implemented but not verified**, or **Deferred**.

### Done and verified

| Brief requirement | How it was verified |
|-------------------|---------------------|
| Custody records for district store → van → health centre, with custodian attribution | Browser walkthrough (transfers, timeline custodians); integration test `condemnedStockCanOnlyBeReturnedAndEveryTransferExtendsTheHashChain` |
| Reading ingestion API suitable for a real logger, plus a simulator using the same API | UI simulator → `POST /api/telemetry/readings` with a device JWT; staff JWTs get 403; tests `rolesAreEnforcedOnTheServer` and `sustainedHeatExcursion…` |
| Configurable WHO-style policies; automatic VIABLE → AT_RISK → CONDEMNED | Live run: CVX-MR-001 condemned after a sustained 1m 34s, while the PQS batch stayed AT_RISK; `ExcursionRulesTest` (5 tests) |
| Condemned stock withdrawn from issuable stock automatically | Live: FEFO refused (`INSUFFICIENT_ISSUABLE_STOCK`), override refused (`BATCH_CONDEMNED`); `condemnedStockIsWithdrawnFromIssuableStock`; `StockServiceTest` |
| Spring Security + JWT, four roles | Live probes as all four roles (Role access page); `rolesAreEnforcedOnTheServer`; storekeeper policy change → 403 |
| SHA-256 tamper-evident custody chain, verifiable through an endpoint | Live: entry #14 linked to #13. Chain verified by the server (`MessageDigest`), in the browser (Web Crypto) and independently in Python `hashlib` (all 14 entries). Tamper → `HASH_MISMATCH` → restore → valid. `LedgerHasherTest` (4 tests, including the SHA-256 `"abc"` vector) and `tamperingWithTheLedgerTableIsDetected` |
| PostgreSQL + Flyway migrations | App log: Flyway applied V1 and V2 on PostgreSQL 17.11 (embedded); Hibernate `ddl-auto=validate` passes |
| Hibernate Envers audit history | Batch page revision history (`system:demo-seed`, `device:LOG-VAN-01`, `system:excursion-worker`, `officer`); test `enversHistoryAttributesEachChangeToItsActor` |
| `@Valid` + central `@RestControllerAdvice` | Invalid transfer → `400 application/problem+json` with field errors; unknown batch → 404 ProblemDetail |
| `@Transactional` + `@Version` FEFO allocator under concurrent issue | `OptimisticLockingIntegrationTest` (2 tests); stale policy edit → `409 CONCURRENT_UPDATE` (live and in test) |
| Every override logged against the officer | Live: FEFO_OVERRIDE, STATUS_RELEASE and POLICY_CHANGE records naming `officer`; tests `officerOverridesAreAttributedToTheOfficer` and `policyChangesAreOfficerOnlyVersionedAndAudited` |
| `@Scheduled` re-evaluation of open excursion windows | Live: PHC logger silenced mid-excursion, worker condemned both batches after 1m 38s (Envers: `system:excursion-worker`); test `silentLoggerCannotHideASustainedExcursionFromTheScheduledWorker` |
| `@Scheduled` expiry and alert digests | Live: digests generated 5 minutes apart (in-app inbox), including the expiry warning for CVX-BOPV-001 |
| Caching of unit state and dashboard aggregates | Test `dashboardAggregatesAreCachedUntilSomethingChanges` (same `generatedAt` until a stock issue evicts it); in-memory cache provider |
| **Redis** caching (`redis` profile) | Docker Redis 7: `cacheProvider = RedisCacheManager`; keys `chainvax::unitState::1` (TTL 60 s) and `chainvax::dashboard::…` created; dashboard key evicted after a status change |
| External **PostgreSQL** (`postgres` profile) + **Docker** (`docker-compose.yml`) | `start-demo.cmd rebuild docker-reset`: containers healthy, Flyway applied V1+V2 on PostgreSQL 17.11 (Debian image), seeded, ledger valid; transfer appended entry #14 (14 rows confirmed with `psql` inside the container) |
| **Testcontainers** | `TestcontainersPostgresIntegrationTest` ran against a `postgres:17-alpine` container: migrations, seed and ledger verify passed |
| Springdoc OpenAPI / Swagger UI | `/v3/api-docs`: 35 paths, `bearerAuth` scheme; `/swagger-ui.html` returns 200 |
| React + Vite + Tailwind UI | `npm run build` (Vite 8); all 7 pages rendered in Chrome with no console errors; Vite dev server and proxy checked |
| Maven, JUnit, Mockito, Testcontainers | `mvnw.cmd -B -o package`: **29 tests, 0 failures, 0 skipped** with Docker running (see section 6). Mockito is used in `StockServiceTest` |

### Implemented but not verified

Nothing remains in this state now that Docker is installed: Redis, the external PostgreSQL profile, `docker-compose.yml` and the Testcontainers test have all been run (see the table above).

### Deferred (out of demo scope)

- Real logger hardware or vendor gateway integration. Only the generic HTTP API and the simulator exist.
- Email, SMS or push delivery of alerts. Digests go to an in-app inbox and the log.
- **External anchoring of the ledger head.** A hash chain stored in the same database cannot stop an attacker with full database write access from recomputing every later hash. Production use would periodically publish or sign the head hash for outside auditors (see Crosby & Wallach in the research notes). The demo detects edits made **without** rewriting the chain, which is what the tamper button simulates.
- Merkle or history-tree inclusion proofs; per-vaccine stability budgets; VVM and shake-test workflows beyond a recorded release reason.
- Production hardening: secret management and key rotation (the demo HS256 key can be overridden with `CHAINVAX_JWT_SECRET`), HTTPS, refresh tokens, rate limiting, multi-district hierarchy, deployment.
- Frontend unit tests.

## 6. Verification log (5 Nov 2026, Windows 11, JDK 25)

| Command | Outcome |
|---------|---------|
| `cd frontend && npm install` | added 75 packages |
| `cd frontend && npm run build` | Vite 8.3.5: 2433 modules → `backend/src/main/resources/static` |
| `cd backend && mvnw.cmd -B -o package` | Without Docker: 29 run, 0 failures, 1 skipped. **With Docker (6 Nov): Tests run: 29, Failures: 0, Errors: 0, Skipped: 0** |
| `start-demo.cmd rebuild docker-reset` | Jar rebuilt; containers recreated and healthy; app on PostgreSQL 17.11 (Docker) + Redis; seeded; ledger valid |
| `java -jar backend/target/chainvax.jar` | Started in 19–35 s (slower the first time); Flyway V1 and V2 applied; "Demo data seeded: 5 users, 3 sites, 3 loggers, 7 batches" |
| `start-demo.cmd` and `start-demo.cmd rebuild` | Existing jar: started in 18 s. Rebuild: Maven Wrapper build succeeded, then started in 19 s |
| Rehearsal of section 3 on a fresh start (Chrome) | Every step gave the documented result; no console errors on any of the 7 pages |
| `GET /v3/api-docs`, `GET /swagger-ui.html` | 200; 35 paths; `bearerAuth` |
| `npx vite` (dev server) | `/` 200; `/api/auth/demo-users` and `/v3/api-docs` proxied with 200 |

Test classes:

| Class | Tests | What it covers |
|-------|-------|----------------|
| `ExcursionRulesTest` | 5 | Threshold classification and durations |
| `LedgerHasherTest` | 4 | Hashing and canonical payload |
| `StockServiceTest` | 5 | FEFO and overrides (Mockito) |
| `DemoScenarioIntegrationTest` | 12 | Full scenario through MockMvc against embedded PostgreSQL |
| `OptimisticLockingIntegrationTest` | 2 | Concurrent issue |
| `TestcontainersPostgresIntegrationTest` | 1 | Same scenario on a throw-away `postgres:17-alpine` container (skips only if Docker is unavailable) |

## 7. Simplifications and assumptions

- **Zero-install database.** PostgreSQL 17.11 runs in-process via zonky `embedded-postgres`, with a fresh temporary database on each start for repeatable demos. Bundled binaries cover Windows x64, Linux x64 and macOS x64. On other platforms (for example ARM64), use the `postgres` profile.
- **Cache.** The in-memory cache is the default. The **`redis`** profile switches the same caches to Redis (60 s TTL, `chainvax::` key prefix).
- **Sites and loggers.** One logger per site, and a reading applies to every batch currently held at that site. There is one district store, one van and one health centre.
- **Policies.**
  - `DEMO-FAST` (heat ≥ 8 °C for 90 s, or cold ≤ −0.5 °C for 60 s, condemns) is labelled demo-only everywhere.
  - `PQS-E006-T1` mirrors the PQS E006/TR06.4 Type 1 logger alarm settings (10 h / 60 min).
  - Both flag AT_RISK as soon as an excursion starts. That grace period is configurable per policy.
- **Time zone.** The app runs its JVM in UTC (`ChainVaxApplication.main`, and `-Duser.timezone=UTC` for tests). Windows reports India's zone as the legacy id `Asia/Calcutta`, which the PostgreSQL 17 Docker image rejects at connection time.
- **Clock skew.** Readings more than 2 minutes in the future are rejected (`FUTURE_READING`).
- **JWT.** HS256 with a symmetric demo key. Tokens are kept in `sessionStorage` by the UI; staff tokens last 8 h and device tokens 12 h.
- **Build differences from the brief's stack.**
  - Spring Boot 4.1 manages **JUnit Jupiter 6.0.3**, the successor to JUnit 5 with the same Jupiter API. The brief lists JUnit 5.
  - The project was written by hand (Maven POM and sources) because the Spring Initializr download was not available in this environment.

## 8. Third-party code and licences

No third-party source files were copied into this repository; everything below is used as a dependency. Licences were read from each artifact's POM or `package.json`.

| Component | Version | Licence |
|-----------|---------|---------|
| Spring Boot / Spring Framework / Spring Security / Spring Data | 4.1.2 / 7.x / 7.1.2 / (Boot-managed) | Apache-2.0 |
| springdoc-openapi | 3.2.0 | Apache-2.0 |
| zonky embedded-postgres + PostgreSQL binaries | 2.2.2 / 17.11.0 | Apache-2.0 (the bundled PostgreSQL server is under the PostgreSQL Licence) |
| PostgreSQL JDBC driver | 42.7.14 | BSD-2-Clause |
| Flyway | 12.4.0 | Apache-2.0 |
| Hibernate ORM + Envers | 7.4.13.Final | Apache-2.0 |
| Nimbus JOSE + JWT | 10.9.1 | Apache-2.0 |
| Lettuce (Redis client) | 7.5.2 | MIT |
| JUnit Jupiter / Mockito / AssertJ / Testcontainers | 6.0.3 / 5.23.0 / 3.27.7 / 2.0.5 | EPL-2.0 / MIT / Apache-2.0 / MIT |
| React, React DOM | 19.3.1 | MIT |
| Vite, @vitejs/plugin-react | 8.3.5, 6.2.1 | MIT |
| Tailwind CSS, @tailwindcss/vite | 4.3.4 | MIT |
| Recharts | 3.10.1 | MIT |
| lucide-react (icons) | 1.68.0 | ISC |
| Maven Wrapper scripts (`mvnw`, `mvnw.cmd`) | 3.3.4 | Apache-2.0 (generated by the Apache Maven Wrapper plugin) |

Repositories reviewed for design (OpenLMIS is AGPL-3.0, so only ideas were taken; the Spring Security JWT sample was a pattern reference) are listed with their licences in [docs/RESEARCH.md](docs/RESEARCH.md#7-repositories-inspected-before-reuse).

## 9. Troubleshooting

- **Port 8080 is in use.** Run `set CHAINVAX_PORT=9090` before `start-demo.cmd`.
- **The window was closed or killed instead of stopped with Ctrl+C.** The embedded database process (`postgres.exe` under `%TEMP%\embedded-pg`) may keep running. The next start still works, because ChainVax picks a free database port automatically. To clean up, run this in PowerShell:
  `Get-CimInstance Win32_Process -Filter "Name='postgres.exe'" | ? CommandLine -match 'embedded-pg' | % { Stop-Process -Id $_.ProcessId -Force }`
- **The first start is slower.** The first start unpacks PostgreSQL and runs `initdb`.
- **Readings are rejected with `FUTURE_READING`.** The logger's clock is more than 2 minutes ahead; check the PC clock.

## 10. Project layout

```
backend/    Spring Boot app (pom.xml, mvnw.cmd), Flyway SQL in src/main/resources/db/migration, tests in src/test
frontend/   React + Vite + Tailwind source (npm run build → backend/src/main/resources/static)
docs/       RESEARCH.md - sources reviewed and how they informed the design
start-demo.cmd (add `docker` for Docker mode), docker-compose.yml (PostgreSQL 17 + Redis 7)
```
