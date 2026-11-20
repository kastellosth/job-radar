# Job Radar

Collects job postings from public job-board APIs, scores each one against my profile, and groups them into **Strong match / Good match / Stretch** so I only read the ones worth applying to.

Java 21 · Spring Boot · PostgreSQL · Flyway · React + TypeScript (Vite) · JUnit · Mockito · Testcontainers · Vitest · Docker · GitHub Actions

## How it works

```
Workable ────────┐
SmartRecruiters ─┤
Greenhouse ──────┼──> IngestService ──> dedupe ──> JobScorer ──> PostgreSQL ──> REST API ──> dashboard
Lever ───────────┤    (08:00 + 18:00 Athens)
Remotive ────────┘
```

- **Sources** (`source/`): one class per provider. Each only fetches and maps JSON into a common `JobPosting`. The company boards (`CompanyBoardSource`) read which companies to fetch from the database on every run. A failing company or provider is logged and skipped; it never stops the others.
- **Deduplication**: the same job is often posted on several boards or reposted with a new id. Company + title + location, lower-cased with accents and punctuation removed, is its identity.
- **Scoring** (`matching/JobScorer`): pure and deterministic. Only software jobs count: a title without a role word (developer, engineer, software, QA, DevOps, προγραμματιστής…) is skipped before any points, so "Junior Talent Acquisition Specialist" cannot ride on its "junior" boost. Then points for skills found anywhere, extra points for title words like "junior", penalties for "senior"/"lead", and −2 per year of required experience above 2. Every point is stored as a readable reason (`+3 java, +4 title:junior, -2 asks for 3+ years`) and shown next to the job.
- **Status**: NEW → SEEN / APPLIED / REJECTED / HIDDEN. When a job shows up again its score is refreshed but my status is kept.
- **Dashboard** (`frontend/`): React + TypeScript, built by Vite. Jobs grouped by bucket; search by title or company (accents and capitals ignored, so `προγραμματιστης` finds `Προγραμματιστής`); filter by company and status; sort by best match or newest; each card says when the job was posted. Opening a NEW job marks it SEEN, so NEW always means "not read yet". Status buttons and **Fetch now**. Maven builds it into the jar, so Spring Boot serves the page and the API from one place (no CORS, one deploy).

All weights, thresholds, accepted cities and role words live in `application.yml`, so tuning the matcher needs no code change. Which companies to read is data, changed in the dashboard (see [Add a company](#add-a-company)).

## Run it

```bash
docker compose up -d                      # PostgreSQL on :5432
API_KEY=change-me ./mvnw spring-boot:run  # app on :8080, Flyway creates the schema
```

The first Maven build downloads Node and builds the dashboard (about a minute). Open http://localhost:8080, type the API key at the top, and press **Fetch now**, or:

```bash
curl localhost:8080/api/jobs
curl -X POST localhost:8080/api/ingest -H 'X-Api-Key: change-me'
curl -X PATCH localhost:8080/api/jobs/42/status -H 'X-Api-Key: change-me' -H 'Content-Type: application/json' -d '{"status":"APPLIED"}'
```

To work on the dashboard with instant reload, keep the app running and start Vite next to it; it forwards `/api` to :8080:

```bash
cd frontend
npm install
npm run dev                               # http://localhost:5173
```

**API key.** Reading is open; every POST, PATCH and DELETE under `/api` needs the `X-Api-Key` header to equal the `API_KEY` environment variable. Without it the answer is `401` with a JSON problem detail. If `API_KEY` is not set, all writes are refused. It is a plain servlet filter (`web/ApiKeyFilter`), not Spring Security: one shared secret for one user needs no users, sessions or login page.

### As a Docker image

The `Dockerfile` builds the jar (and the dashboard inside it) with Maven, then copies it into a small JRE 21 image that runs as a non-root user.

```bash
docker build -t job-radar .
docker run -p 8080:8080 -e API_KEY=change-me -e DB_URL=jdbc:postgresql://host.docker.internal:5432/jobradar job-radar
```

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | HTTP port (Render sets it) |
| `API_KEY` | none | Required in `X-Api-Key` for POST/PATCH; writes are refused when it is not set |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobradar` | JDBC URL of the database |
| `DB_USER` / `DB_PASSWORD` | `jobradar` / `jobradar` | Database login |

### Deploy to Render

Create a **Web Service** from this repository with runtime **Docker**, set `API_KEY` to a long random value, and set `DB_URL`, `DB_USER` and `DB_PASSWORD` to a hosted PostgreSQL (for example Neon). Flyway creates the schema on the first start.
On the free plan the service sleeps after 15 minutes without requests, so the 08:00 and 18:00 runs only happen if it is awake; use **Fetch new jobs now** (or `POST /api/ingest`) after opening it.

## Add a company

Open **Companies** in the dashboard, paste the company's careers page link and press **Add**. The app recognises the job board from the link, asks that board once whether the company exists (so a typo is refused with a clear message, not saved), and reads it from the next fetch on, without a restart. **remove** stops reading a company; the jobs already collected stay, with your statuses.

| Careers page link looks like | Job board |
|---|---|
| `apply.workable.com/<slug>` or `<slug>.workable.com` | Workable |
| `boards.greenhouse.io/<token>` or `job-boards.greenhouse.io/<token>` | Greenhouse |
| `jobs.lever.co/<slug>` | Lever |
| `careers.smartrecruiters.com/<CompanyId>` | SmartRecruiters |

The same with the API (POST and DELETE need the API key):

```bash
curl localhost:8080/api/companies
curl -X POST localhost:8080/api/companies -H 'X-Api-Key: change-me' -H 'Content-Type: application/json' -d '{"url":"https://apply.workable.com/learnworlds"}'
curl -X DELETE localhost:8080/api/companies/7 -H 'X-Api-Key: change-me'
```

Answers: `201` with the company and how many jobs it has open now; `400` for a link that is not a supported careers page or a company the board does not know; `409` if it is already tracked (capitals do not matter); `502` if the job board does not answer.

The companies live in the `tracked_company` table. Migration `V2` starts it with 16 companies that have software jobs in Greece (European Dynamics, Qualco, Agile Actors, Uni Systems, LearnWorlds, Kaizen Gaming and others), each checked against its live API. SmartRecruiters answers an unknown company id with an empty list instead of `404`, so a SmartRecruiters company with no jobs is logged as a warning.

Remotive is read once per run (`radar.sources.remotive: true`): its free API returns the same short list whatever the search term, and its terms ask for at most four calls a day and a visible link back, which the dashboard shows as "via Remotive".

LinkedIn, Glassdoor and similar sites are deliberately not scraped: their terms forbid it. Only public, documented job-board feeds are used.

## Tests

```bash
./mvnw test      # Java unit tests and the dashboard tests: no Docker
./mvnw verify    # also the *IT integration tests: needs Docker running
cd frontend && npm test   # only the dashboard tests
```

Unit tests:

- `JobScorerTest`: the software-role filter (in English and Greek, also in capitals without accents), scoring rules, word boundaries (`java` ≠ `javascript`, `node.js` and `c#` still match), location filter, experience parsing in English and Greek, bucket thresholds.
- `JobPostingTest`: deduplication across boards, Greek accents, remote jobs.
- `SourceParsingTest`: each provider mapped from a saved sample response, no network. Greenhouse jobs are dated by `first_published`, not `updated_at` (which changes whenever the ad is edited).
- `CareersLinkTest`: careers page links of each board become the right board and company id (trailing slashes, query strings, capitals); LinkedIn, plain websites, a link to a single job and broken text are refused.
- `CompanyServiceTest`: a company is saved only after its board confirms it; unknown company, already tracked (also when two requests race) and unsupported links are refused before saving.
- `CompanyControllerTest`: `201`/`400`/`409`/`502`/`404` answers as problem details, and adding or removing needs the API key.
- `SourceFetchTest`: what the sources request, against a fake HTTP server: every tracked company is read and one that fails is skipped, Remotive is called exactly once (or not at all when switched off), and a SmartRecruiters company with no jobs logs a warning.
- `ApiKeyFilterTest`, `ApiKeyFilterWithoutKeyTest`: POST/PATCH without or with a wrong key get `401` and never reach the service, the right key passes, GET needs no key, and a server without `API_KEY` refuses every write.
- `IngestServiceTest`: duplicates stored once, status survives a refresh, one broken source doesn't stop the run.

Dashboard tests (Vitest + Testing Library, with `fetch` replaced by a fake server):

- `App.test.tsx`: jobs grouped by bucket; ticked statuses become the API query (none ticked: no request); company filter and search work in the page without a new request; opening a NEW job marks it SEEN at once and sends the key, while a job that is not NEW, or a missing key, sends nothing; status buttons and **Fetch now** send the API key and reload; a `401` points at the key field.
- `jobView.test.ts`: search (every word, title or company, no accents), company filter, newest-first sorting with first-seen as fallback, company counts, and "3 days ago" dates.
- `CompaniesPanel.test.tsx`: lists the companies; adding and removing send the API key and reload the list; a refused link shows the server's reason and keeps the text.
- `JobCard.test.tsx`: only `http(s)` addresses become links (never `javascript:`), the button for the current status is disabled, "posted"/"found" dates, and opening the link is reported.

Integration tests (Testcontainers starts a throw-away `postgres:16-alpine`; the Flyway migrations build the schema):

- `TrackedCompanyRepositoryIT`: migration `V2` starts with the 16 companies, and the unique index refuses the same company with other capitals but allows the same name on another board.
- `JobRepositoryIT`: lookup by fingerprint, the UNIQUE constraint rejects a duplicate, the dashboard query hides SKIP/HIDDEN jobs and sorts best first.
- `IngestServiceIT`: a full ingest in the real Spring context with stub job boards: real scoring from `application.yml`, the same job from two boards stored once, a refreshed score is really written to the database while his status is kept, and a board that is down doesn't stop the others.

## Roadmap

- [x] React + TypeScript dashboard (replace `static/index.html`)
- [x] Testcontainers integration test for the repository and the full ingest
- [x] Add and remove tracked companies from the dashboard
- [ ] Fetch full SmartRecruiters job descriptions for promising titles
- [ ] Weekly email digest of new STRONG matches
- [ ] Optional LLM re-ranking of the top 20 jobs against the CV
