# Job Radar

Collects job postings from public job-board APIs, scores each one against my profile, and groups them into **Strong match / Good match / Stretch** so I only read the ones worth applying to.

Java 21 · Spring Boot · PostgreSQL · Flyway · JUnit · Mockito · Docker · GitHub Actions

## How it works

```
Workable ────────┐
SmartRecruiters ─┤
Greenhouse ──────┼──> IngestService ──> dedupe ──> JobScorer ──> PostgreSQL ──> REST API ──> dashboard
Lever ───────────┤    (08:00 + 18:00 Athens)
Remotive ────────┘
```

- **Sources** (`source/`): one class per provider. Each only fetches and maps JSON into a common `JobPosting`. A failing company or provider is logged and skipped; it never stops the others.
- **Deduplication**: the same job is often posted on several boards or reposted with a new id. Company + title + location, lower-cased with accents and punctuation removed, is its identity.
- **Scoring** (`matching/JobScorer`): pure and deterministic. Points for skills found anywhere, extra points for title words like "junior", penalties for "senior"/"lead", and −2 per year of required experience above 2. Every point is stored as a readable reason (`+3 java, +4 title:junior, -2 asks for 3+ years`) and shown next to the job.
- **Status**: NEW → SEEN / APPLIED / REJECTED / HIDDEN. When a job shows up again its score is refreshed but my status is kept.

All weights, thresholds, accepted cities and company slugs live in `application.yml`, so tuning the matcher needs no code change.

## Run it

```bash
docker compose up -d          # PostgreSQL on :5432
./mvnw spring-boot:run        # app on :8080, Flyway creates the schema
```

Open http://localhost:8080 and press **Fetch new jobs now**, or:

```bash
curl -X POST localhost:8080/api/ingest
curl localhost:8080/api/jobs
curl -X PATCH localhost:8080/api/jobs/42/status -H 'Content-Type: application/json' -d '{"status":"APPLIED"}'
```

### As a Docker image

The `Dockerfile` builds the jar with Maven, then copies it into a small JRE 21 image that runs as a non-root user.

```bash
docker build -t job-radar .
docker run -p 8080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5432/jobradar job-radar
```

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | HTTP port (Render sets it) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobradar` | JDBC URL of the database |
| `DB_USER` / `DB_PASSWORD` | `jobradar` / `jobradar` | Database login |

### Deploy to Render

Create a **Web Service** from this repository with runtime **Docker**, and set `DB_URL`, `DB_USER` and `DB_PASSWORD` to a hosted PostgreSQL (for example Neon). Flyway creates the schema on the first start.
On the free plan the service sleeps after 15 minutes without requests, so the 08:00 and 18:00 runs only happen if it is awake; use **Fetch new jobs now** (or `POST /api/ingest`) after opening it.

## Add a company

Find the company's careers page and copy its slug into `application.yml`:

| Careers URL looks like | Add under |
|---|---|
| `apply.workable.com/<slug>` | `radar.sources.workable` |
| `careers.smartrecruiters.com/<CompanyId>` | `radar.sources.smartrecruiters` |
| `boards.greenhouse.io/<token>` | `radar.sources.greenhouse` |
| `jobs.lever.co/<slug>` | `radar.sources.lever` |

LinkedIn, Glassdoor and similar sites are deliberately not scraped: their terms forbid it. Only public, documented job-board feeds are used.

## Tests

```bash
./mvnw test      # unit tests only: fast, no Docker
./mvnw verify    # also the *IT integration tests: needs Docker running
```

Unit tests:

- `JobScorerTest`: scoring rules, word boundaries (`java` ≠ `javascript`, `node.js` and `c#` still match), location filter, experience parsing in English and Greek, bucket thresholds.
- `JobPostingTest`: deduplication across boards, Greek accents, remote jobs.
- `SourceParsingTest`: each provider mapped from a saved sample response, no network.
- `IngestServiceTest`: duplicates stored once, status survives a refresh, one broken source doesn't stop the run.

Integration tests (Testcontainers starts a throw-away `postgres:16-alpine`; the Flyway migrations build the schema):

- `JobRepositoryIT`: lookup by fingerprint, the UNIQUE constraint rejects a duplicate, the dashboard query hides SKIP/HIDDEN jobs and sorts best first.
- `IngestServiceIT`: a full ingest in the real Spring context with stub job boards: real scoring from `application.yml`, the same job from two boards stored once, a refreshed score is really written to the database while his status is kept, and a board that is down doesn't stop the others.

## Roadmap

- [ ] React + TypeScript dashboard (replace `static/index.html`)
- [x] Testcontainers integration test for the repository and the full ingest
- [ ] Fetch full SmartRecruiters job descriptions for promising titles
- [ ] Weekly email digest of new STRONG matches
- [ ] Optional LLM re-ranking of the top 20 jobs against the CV
