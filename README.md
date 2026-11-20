# Job Radar

Collects job postings from company job boards, scores each one against my profile, and groups them into
**Strong match / Good match / Stretch**, so I only read the ones worth applying to.

Java 21 · Spring Boot · PostgreSQL · Flyway · React + TypeScript (Vite) · JUnit · Testcontainers · Vitest · Docker · GitHub Actions

```
Workable ────────┐
SmartRecruiters ─┤
Greenhouse ──────┤
Lever ───────────┼──> IngestService ──> dedupe ──> JobScorer ──> PostgreSQL ──> REST API ──> dashboard
Workday ─────────┤    (08:00 + 18:00 Athens)
SuccessFactors ──┤
Remotive ────────┘
```

## What it does

- **Reads the boards of the companies you follow**: Workable, Greenhouse, Lever, SmartRecruiters, Workday (Accenture, PwC…) and SuccessFactors (EY, Vodafone…), plus Remotive for remote jobs. You add a company by pasting its careers link.
- **Scores every job** with readable reasons (`+3 java, +4 title:junior, -2 asks for 3+ years`); all weights live in `application.yml`.
- **Shows the pay** when the board or the ad states it, in English or Greek, and can filter to those jobs.
- **Keeps track**: NEW → SEEN / APPLIED / REJECTED / HIDDEN; closed jobs leave the list, and a board that breaks shows in red instead of silently emptying it.
- **Emails the week's new strong matches** every Monday, if you set it up.

How each part works and why: [docs/architecture.md](docs/architecture.md).

## Run it

```bash
docker compose up -d                      # PostgreSQL on :5432
API_KEY=change-me ./mvnw spring-boot:run  # app on :8080; Flyway creates the schema
```

The first build downloads Node and builds the dashboard (about a minute). Open http://localhost:8080, type the API key
at the top and press **Fetch now**.

Reading is open; every POST, PATCH and DELETE under `/api` needs the `X-Api-Key` header to equal `API_KEY` (writes are
refused when it is not set).

## Add a company

Open **Companies** in the dashboard, paste the company's careers link and press **Add**. The board is recognised from
the link and asked once whether the company exists:

| Careers link looks like | Job board |
|---|---|
| `apply.workable.com/<slug>` or `<slug>.workable.com` | Workable |
| `boards.greenhouse.io/<token>` | Greenhouse |
| `jobs.lever.co/<slug>` | Lever |
| `careers.smartrecruiters.com/<CompanyId>` | SmartRecruiters |
| `<tenant>.wd<N>.myworkdayjobs.com/<Site>?locationCountry=…` | Workday: filter to Greece on the site first |
| `<company domain>/…/search/?locationsearch=Greece` | SuccessFactors: search for Greece on the site first |

Details for each board, and the same through the API: [docs/job-boards.md](docs/job-boards.md).

## Configure and deploy

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | HTTP port (Render sets it) |
| `API_KEY` | none | Required in `X-Api-Key` for writes; writes are refused when it is not set |
| `DB_URL` | `jdbc:postgresql://localhost:5432/jobradar` | JDBC URL of the database |
| `DB_USER` / `DB_PASSWORD` | `jobradar` / `jobradar` | Database login |
| `DIGEST_TO` | none | Address for the weekly email; the email is off while it is empty |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | none | Gmail address and app password that send it |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | Another SMTP server instead of Gmail |
| `DASHBOARD_URL` | none | Link to the dashboard at the bottom of the email |

**Docker**: `docker build -t job-radar .` builds the jar and the dashboard into a small JRE 21 image that runs as a
non-root user.

**Render**: create a **Web Service** from this repository with runtime **Docker**, set `API_KEY` to a long random value
and `DB_URL`, `DB_USER`, `DB_PASSWORD` to a hosted PostgreSQL (for example Neon). On the free plan the service sleeps
after 15 minutes without requests, so the scheduled fetches and the Monday email only happen while it is awake; open it
and press **Fetch now**.

**Weekly email**: turn on 2-Step Verification for the Gmail account and create an app password (Google account >
Security > App passwords); set `MAIL_USERNAME`, `MAIL_PASSWORD` and `DIGEST_TO`. Check it without waiting for Monday:
`curl -X POST -H "X-Api-Key: $API_KEY" https://<your-app>/api/digest`. A wrong password answers `502` and the reason
goes to the log.

## Contributing

Setup, tests, code style and how to add a job board: [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

- [x] React + TypeScript dashboard
- [x] Testcontainers integration tests for the repository and the full ingest
- [x] Add and remove tracked companies from the dashboard
- [x] Full SmartRecruiters job descriptions for promising titles
- [x] Weekly email digest of new STRONG matches
- [x] Workday and SuccessFactors career sites, board health, pay
- [ ] Optional LLM re-ranking of the top 20 jobs against the CV

## License

[MIT](LICENSE)
