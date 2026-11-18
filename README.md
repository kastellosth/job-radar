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
./mvnw test
```

- `JobScorerTest`: scoring rules, word boundaries (`java` ≠ `javascript`, `node.js` and `c#` still match), location filter, experience parsing in English and Greek, bucket thresholds.
- `JobPostingTest`: deduplication across boards, Greek accents, remote jobs.
- `SourceParsingTest`: each provider mapped from a saved sample response, no network.
- `IngestServiceTest`: duplicates stored once, status survives a refresh, one broken source doesn't stop the run.

## Roadmap

- [ ] React + TypeScript dashboard (replace `static/index.html`)
- [ ] Testcontainers integration test for the repository and the full ingest
- [ ] Fetch full SmartRecruiters job descriptions for promising titles
- [ ] Weekly email digest of new STRONG matches
- [ ] Optional LLM re-ranking of the top 20 jobs against the CV
