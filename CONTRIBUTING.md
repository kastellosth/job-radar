# Contributing to Job Radar

Thank you for helping. This page has what you need to make a change and get it merged.

## Set up

You need **JDK 21** (a recent update: older 21 builds lack some root certificates, e.g. the one EY's careers site uses)
and **Docker** (for PostgreSQL and the integration tests). Node is downloaded by Maven; install it yourself only to run
the dashboard's dev server.

```bash
docker compose up -d                      # PostgreSQL on :5432
API_KEY=change-me ./mvnw spring-boot:run  # app on :8080

cd frontend && npm install && npm run dev # dashboard with instant reload on :5173, forwards /api to :8080
```

## Find your way

[docs/architecture.md](docs/architecture.md) explains how a fetch flows and why things are the way they are; read it
before a larger change. In short:

| Where | What |
|---|---|
| `src/main/java/.../source/` | One class per job board: fetch, map into `JobPosting`, recognise its links |
| `src/main/java/.../matching/` | `JobScorer`, `PayParser`: pure functions |
| `src/main/java/.../service/` | Ingest, companies, weekly email |
| `src/main/java/.../domain/` | Entities and repositories |
| `src/main/java/.../web/` | Controllers, API key filter, `JobView` |
| `src/main/resources/application.yml` | Scoring weights, places, schedule |
| `src/main/resources/db/migration/` | Flyway migrations |
| `frontend/src/` | React dashboard (`api.ts` mirrors the API's shapes) |

## Test

```bash
./mvnw test       # Java unit tests and dashboard tests; no Docker needed
./mvnw verify     # also the *IT integration tests (Docker) and the style check; what CI runs
cd frontend && npm test && npm run lint
```

Where a test goes:

- **Pure logic** (`matching/`, `domain/` records, link parsing): plain unit tests, many small cases
  (`JobScorerTest`, `PayParserTest`, `CareersLinkTest`).
- **A source's mapping**: `SourceParsingTest`, from a saved sample answer in `src/test/resources/fixtures/`. Shorten real
  answers to what the test needs, keep their real structure, and never put personal data in them.
- **What a source requests**: `SourceFetchTest`, against Spring's `MockRestServiceServer`. Tests never call the
  internet.
- **Controllers**: `@WebMvcTest` with the service mocked, including the API key (`JobControllerTest`).
- **SQL and the whole ingest**: `*IT` classes against a throw-away PostgreSQL started by Testcontainers.
- **Dashboard**: Vitest + Testing Library, with `fetch` replaced by a fake server (`App.test.tsx`).

A change in behaviour comes with a test that fails without it.

## Code style

- Java: `./mvnw verify` runs Spotless (no unused imports, no trailing spaces, no tabs, final newline);
  `./mvnw spotless:apply` fixes it. Otherwise follow the code around you: 4 spaces, about 120 columns.
- TypeScript: `npm run lint` (oxlint); 2 spaces, no semicolons, single quotes.
- `.editorconfig` sets this up in most editors. Text files use LF line endings.
- Comments say **why**, not what. Name things for what they mean to the user ("closed", "failing board").
- Write the user as "you" in comments and docs.

## Database changes

Add a new migration, `V<next>__what_it_does.sql`. **Never edit a migration that has been merged**: Flyway checks each
applied migration's checksum and refuses to start when one changed, on every database that already ran it. The entity
must match (`spring.jpa.hibernate.ddl-auto: validate` fails at startup otherwise), and `*IT` tests run the migrations
for real.

## Add a job board

1. A class in `source/`: extend `CompanyBoardSource` for boards with one list per company (or implement `JobSource`).
   Map the answer into `JobPosting` in a static `parse` method, so it can be tested without HTTP.
2. A `static CareersLink link(Link link)` in it that answers `null` for links that are not its own, and one line for
   it in `CareersLink.BOARDS`.
3. A value in `Provider`, and a migration that adds it to the `tracked_company_provider_check` constraint.
4. If its list has no descriptions, override `complete` and use `readAds` for promising jobs only.
5. Tests: parsing from a fixture, requests in `SourceFetchTest`, links in `CareersLinkTest`.
6. Respect the board: its terms and `robots.txt`, few requests (two runs a day), no logins.
7. A section in [docs/job-boards.md](docs/job-boards.md) and a row in the README's table.

## Send a change

1. Fork, make a branch from `main`, and keep each pull request about one thing.
2. Commit messages follow the existing style: `feat: …`, `fix: …`, `refactor: …`, `docs: …`, `chore: …`, then a body
   that says why.
3. Before opening the pull request: `./mvnw verify` passes, `npm run lint` is clean, and the docs say what changed.
4. Describe what the change does and how you checked it.

By contributing you agree that your contribution is licensed under the project's [MIT License](LICENSE).
