# Job Radar

Collects job postings from public job-board APIs, scores each one against my profile, and groups them into **Strong match / Good match / Stretch** so I only read the ones worth applying to.

Java 21 · Spring Boot · PostgreSQL · Flyway · React + TypeScript (Vite) · JUnit · Mockito · Testcontainers · Vitest · Docker · GitHub Actions

## How it works

```
Workable ────────┐
SmartRecruiters ─┤
Greenhouse ──────┤
Lever ───────────┼──> IngestService ──> dedupe ──> JobScorer ──> PostgreSQL ──> REST API ──> dashboard
Workday ─────────┤    (08:00 + 18:00 Athens)
SuccessFactors ──┤
Remotive ────────┘
```

- **Sources** (`source/`): one class per provider. Each only fetches and maps JSON into a common `JobPosting`. The company boards (`CompanyBoardSource`) read which companies to fetch from the database on every run. A failing company or provider is logged and skipped; it never stops the others.
- **Deduplication**: the same job is often posted on several boards or reposted with a new id. Company + title + location, lower-cased with accents and punctuation removed, is its identity.
- **Scoring** (`matching/JobScorer`): pure and deterministic. Only software jobs count: a title without a role word (developer, engineer, software, QA, DevOps, προγραμματιστής…) is skipped before any points, so "Junior Talent Acquisition Specialist" cannot ride on its "junior" boost. Then points for skills found anywhere, extra points for title words like "junior", penalties for "senior"/"lead"/"manager" in the title only (ads for junior roles often mention "senior engineers" or "the engineering manager"), penalties for words like "German" anywhere in the ad, and −2 per year of required experience above 2. Every point is stored as a readable reason (`+3 java, +4 title:junior, -2 asks for 3+ years`) and shown next to the job.
- **Status**: NEW → SEEN / APPLIED / REJECTED / HIDDEN. When a job shows up again its score is refreshed but my status is kept. A job is taken as closed, and leaves the dashboard, once its board has been read fine for 3 days (`hide-unlisted-after`) without listing it; jobs I applied to stay.
- **Board health**: every read of every board (each tracked company, and Remotive) is recorded in `board_health`: when it last worked, how many jobs it listed, and since when it fails. A board that is down or broken therefore keeps its jobs on the dashboard instead of having them closed, and a board that suddenly lists no jobs after listing some counts as failing too, since that is far more often a broken page or a wrong id than a company closing every opening at once. **Companies** shows each board as "read today · 12 jobs" or, in red, "failing since 18 Nov: …", and its heading counts the failing ones.
- **Pay** (`matching/PayParser`): when a board has a pay field it is used (Lever's `salaryRange`, Remotive's `salary`); otherwise the ad's own text is read, in English or Greek: "€30,000 - €40,000 gross per year", "30-40k EUR", "Μικτές μηνιαίες αποδοχές: 1.200 - 1.500 €". An amount only counts with a currency, a pay word close by (salary, gross, net, compensation, μισθός, αποδοχές, αμοιβή, μικτά, καθαρά), no benefit word right next to it (bonus, meal voucher, relocation, επίδομα...), and a plausible size for its period (a month of €300–40,000, a year of €5,000–600,000); without a period word, five figures are a year and three or four a month. Missing a stated salary is preferred to showing a relocation bonus as pay. Nothing is converted: a monthly gross stays monthly, and the sentence it came from is kept, so gross or net and "14 salaries" can be read as written. Few Greek software ads state pay yet; the EU pay transparency rules are meant to change that.
- **Fetching**: the boards are read before the database transaction opens, so a slow board never holds a connection, and only one fetch runs at a time: pressing **Fetch now** during the scheduled run answers `409` with a message instead of racing it.
- **Dashboard** (`frontend/`): React + TypeScript, built by Vite. Jobs grouped by bucket; search by title or company (accents and capitals ignored, so `προγραμματιστης` finds `Προγραμματιστής`); filter by company and status, or to the jobs that show pay; sort by best match or newest; each card shows the date the job was posted and how long ago, both counted in Athens calendar days. Cards show the pay when the board or ad gives it ("€30,000–€40,000 a year"; hover for the sentence it came from). Buckets have their own colour (green, teal, amber) on the title, card edge and score. Opening a NEW job marks it SEEN, so NEW always means "not read yet". Status buttons and **Fetch now**. An empty bucket says whether it is still loading, the server failed, or the search hid its jobs. Reports like "Fetched 120, 3 new" fade after 8 seconds; errors stay until closed. Maven builds it into the jar, so Spring Boot serves the page and the API from one place (no CORS, one deploy).

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
| `DIGEST_TO` | none | Address for the weekly email; the email is off while it is empty |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | none | Gmail address and app password that send it |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | Another SMTP server instead of Gmail |
| `DASHBOARD_URL` | none | Link to the dashboard at the bottom of the email |

### Deploy to Render

Create a **Web Service** from this repository with runtime **Docker**, set `API_KEY` to a long random value, and set `DB_URL`, `DB_USER` and `DB_PASSWORD` to a hosted PostgreSQL (for example Neon). Flyway creates the schema on the first start.
On the free plan the service sleeps after 15 minutes without requests, so the 08:00 and 18:00 runs only happen if it is awake; use **Fetch now** (or `POST /api/ingest`) after opening it. The same goes for the Monday email.

### Weekly email

Every Monday at 08:30 (Athens), right after the morning fetch, an email lists the STRONG matches first seen in the last 7 days that are still listed and still NEW or SEEN, best first, each with its company, place, posting date, score and link. A week without any sends nothing.

1. Turn on 2-Step Verification for the Gmail account, then create an app password (Google account > Security > App passwords). The normal Gmail password does not work over SMTP.
2. Set `MAIL_USERNAME` to the Gmail address, `MAIL_PASSWORD` to the app password and `DIGEST_TO` to where the email should go (it can be the same address). Optionally `DASHBOARD_URL`.
3. Check it without waiting for Monday:

```bash
curl -X POST -H "X-Api-Key: $API_KEY" https://<your-app>/api/digest   # {"jobs":3}; 0 means nothing new, no email
```

A wrong password answers `502` and the mail server's reason goes to the log. Some hosts block outgoing SMTP on free plans; if the call times out there, set `MAIL_HOST`/`MAIL_PORT` to a provider that accepts SMTP on another port, or check the host's plan. The day and time are `radar.digest.schedule` in `application.yml`.

## Add a company

Open **Companies** in the dashboard, paste the company's careers page link and press **Add**. The app recognises the job board from the link, asks that board once whether the company exists (so a typo is refused with a clear message, not saved), and reads it from the next fetch on, without a restart. **remove** (after a confirmation) stops reading a company; its jobs keep their statuses in the database but, with no board reading them any more, leave the dashboard after 3 days, except the ones you applied to. Lever's API gives no company name, so a Lever company gets one from its link (`kaizen-gaming` → "Kaizen Gaming"), and its jobs show that name.

| Careers page link looks like | Job board |
|---|---|
| `apply.workable.com/<slug>` or `<slug>.workable.com` | Workable |
| `boards.greenhouse.io/<token>` or `job-boards.greenhouse.io/<token>` | Greenhouse |
| `jobs.lever.co/<slug>` | Lever |
| `careers.smartrecruiters.com/<CompanyId>` | SmartRecruiters |
| `<tenant>.wd<N>.myworkdayjobs.com/<Site>?locationCountry=…` | Workday |
| `<company's careers domain>/…/search/?locationsearch=Greece` | SuccessFactors |

**Workday** sites (Accenture, PwC and many large employers) list a company's jobs worldwide, so filter the job search to Greece on the company's site first (for example the *Location / Country* filter) and paste the link from the address bar. The filters in it are kept (`locationCountry=566388c1…`; tracking parameters are dropped) and every fetch repeats that exact search through the JSON the site's own pages use, 20 jobs per page. A search that lists more than 500 jobs is refused, as a sign the link was not filtered. Its list gives only a title and a rough date ("Posted 9 Days Ago"; "30+ Days Ago" gives none), so, as with SmartRecruiters, the full ad is read for the jobs that could score: the description, the real posting date, and the offices, listing the accepted ones first (a London job open in Athens shows "Athens, …"). Workday gives no company name, so the company is named from its link (`accenture` → "Accenture", `pwc` → "PWC"). The search is not a documented API: if Workday changes it, the company turns red in **Companies** and its jobs stay until it is fixed.

**SuccessFactors** sites (EY, Vodafone, Kotsovolos and many large employers) live on the company's own domain (`careers.ey.com`, `opportunities.vodafone.com`, `career.kotsovolos.gr`) and have no public API, so a link is recognised by its shape, a path ending in `/search/`, and adding it checks that the page really is a SuccessFactors search. Search for Greece on the site and paste the results link; the filters that choose jobs (`q`, `locationsearch`, `locale`, `optionsFacetsDD_…`) are kept and the rest dropped. Each fetch reads the results pages themselves, the part `robots.txt` allows (the RSS feed under `/services/` is not, and ignores the location anyway): the table layout (EY, Vodafone: 25 per page, "Results 1 – 25 of 76") and the card layout (Kotsovolos: 30 per page), walked with `startrow` until the total is reached or a page brings nothing new. More than 500 jobs is refused as unfiltered. A lone country code is written out ("Athens, GR" → "Athens, Greece") so the location filter recognises it, and "+4 more…" is dropped. For the jobs that could score, the job's own page gives the description and the posting date. The company is named from its domain (`ey` → "EY"). Reading pages is the most fragile kind of source here: a redesign breaks it, which then shows in red in **Companies** while its jobs stay.

The same with the API (POST and DELETE need the API key):

```bash
curl localhost:8080/api/companies
curl -X POST localhost:8080/api/companies -H 'X-Api-Key: change-me' -H 'Content-Type: application/json' -d '{"url":"https://apply.workable.com/learnworlds"}'
curl -X DELETE localhost:8080/api/companies/7 -H 'X-Api-Key: change-me'
```

Answers: `201` with the company and how many jobs it has open now; `400` for a link that is not a supported careers page or a company the board does not know; `409` if it is already tracked (capitals do not matter); `502` if the job board does not answer.

The companies live in the `tracked_company` table. Migration `V2` starts it with 16 companies that have software jobs in Greece (European Dynamics, Qualco, Agile Actors, Uni Systems, LearnWorlds, Kaizen Gaming and others), each checked against its live API. SmartRecruiters answers an unknown company id with an empty list instead of `404`, so a SmartRecruiters company with no jobs is logged as a warning. Its job list has no descriptions either: on each fetch the full ad is read for the jobs that could score (accepted city or remote, software title), at most 50 per company, and the others are scored on title, department and experience level. The ad's company blurb is left out, since it is the same on every ad. Adding a company only reads the list.

Remotive is read once per run (`radar.sources.remotive: true`): its free API returns the same short list whatever the search term, and its terms ask for at most four calls a day and a visible link back, which the dashboard shows as "via Remotive".

LinkedIn, Glassdoor and similar sites are deliberately not scraped: their terms forbid it. Only public, documented job-board feeds are used.

## Tests

```bash
./mvnw test      # Java unit tests and the dashboard tests: no Docker
./mvnw verify    # also the *IT integration tests: needs Docker running
cd frontend && npm test   # only the dashboard tests
```

Unit tests:

- `JobScorerTest`: places in Greek capitals without accents ("ΑΘΗΝΑ", "ΝΕΟ ΗΡΑΚΛΕΙΟ") are accepted; the software-role filter (in English and Greek, also in capitals without accents), scoring rules, seniority words counted only in the title, word boundaries (`java` ≠ `javascript`, `node.js` and `c#` still match), location filter, experience parsing in English and Greek, bucket thresholds.
- `JobPostingTest`: deduplication across boards, Greek accents, remote jobs.
- `SourceParsingTest`: each provider mapped from a saved sample response, no network. Lever's salary range and Remotive's salary field become pay. The SmartRecruiters ad text keeps the job's own sections and drops the company blurb and HTML. Greenhouse jobs are dated by `first_published`, not `updated_at` (which changes whenever the ad is edited). SuccessFactors: the table (title, link, place with the country written out, English date, total from "Results 1 – 2 of 3") and the cards (value without its label, total from the page script, a Greek date left to the job page), the job page's description and `datePosted`, a page that is not a SuccessFactors search refused, the search read back from the slug. Workday: title, link and place from the list, "2 Locations" left empty, "Posted N Days Ago" as an Athens day and "30+ Days Ago" as no date, and the search read back from the slug.
- `CareersLinkTest`: careers page links of each board become the right board and company id (trailing slashes, query strings, capitals); LinkedIn, plain websites, a link to a single job and broken text are refused. A Workday search keeps its filters, sorted, and drops tracking parameters, so the same search pasted twice is the same company; a Workday link without its job search, or to one job, is refused; companies are named from their link (`pwc` → "PWC"). SuccessFactors searches on a company's own domain keep the filters that choose jobs and drop the rest; an IP address, `localhost` or a link without `/search/` is refused.
- `CompanyServiceTest`: a company is saved only after its board confirms it, named after its jobs or, when the board only repeats the slug, after the slug; unknown company, already tracked (also when two requests race) and unsupported links are refused before saving; the list carries each board's health, and removing a company forgets it.
- `CompanyControllerTest`: the list shows each board's last good read, job count and failure; `201`/`400`/`409`/`502`/`404` answers as problem details, and adding or removing needs the API key.
- `SourceFetchTest`: what the sources request, against a fake HTTP server: every tracked company is read as its own board and one that fails comes back as a failed read (Remotive too), not an exception, Remotive is called exactly once (or not at all when switched off), a SmartRecruiters company with no jobs logs a warning, jobs from a board without company names get the tracked company's name, SmartRecruiters reads the full ad only for software jobs in accepted places, keeps the short description when the ad cannot be read, and reads only the list when a company is added. Workday repeats the filtered search page by page (the request body is checked), reads the full ad only for software jobs, keeps a job as listed when its ad fails, lists the accepted office first, and refuses a search of more than 500 jobs. SuccessFactors walks the result pages with `startrow`, reads job pages only for software jobs, keeps a job as listed when its page fails, refuses more than 500 jobs, and stops when a page adds nothing new.
- `DigestMailTest`: the email lists each job with company, place, Athens posting date, pay when given, score, reasons and link; the subject counts the jobs; titles from job boards are escaped and non-http links dropped.
- `DigestServiceTest`: asks for STRONG jobs of the last 7 days that are NEW or SEEN and still listed; sends one email, or none in an empty week; without `DIGEST_TO` the schedule does nothing and a manual send is refused; a mail failure on the schedule is logged, not thrown.
- `DigestControllerTest`: `POST /api/digest` needs the key, answers how many jobs were sent, `409` when the email is not set up, `502` without the mail server's message when sending fails.
- `ApiKeyFilterTest`, `ApiKeyFilterWithoutKeyTest`: POST/PATCH without or with a wrong key get `401` and never reach the service, the right key passes, GET needs no key, and a server without `API_KEY` refuses every write.
- `IngestServiceTest`: duplicates stored once, status survives a refresh, one broken source doesn't stop the run, a second run is refused while the first is still fetching, each board's health is recorded and each job remembers its board. Pay is read from the ad when the board has no pay field.
- `BoardHealthTest`: a good read records when and how many jobs, failures keep the last good read and when failing began, a good read clears them, no jobs after some is a failure, a board that never had jobs may stay empty, long errors are cut.

Dashboard tests (Vitest + Testing Library, with `fetch` replaced by a fake server):

- `App.test.tsx`: jobs grouped by bucket; ticked statuses become the API query (none ticked: no request); company filter and search work in the page without a new request; opening a NEW job marks it SEEN at once and sends the key, while a job that is not NEW, or a missing key, sends nothing; status buttons and **Fetch now** send the API key and reload; a `401` points at the key field; an empty bucket says "loading", "could not load" or "no matches" when that is the reason; the fetch report fades while errors stay.
- `jobView.test.ts`: search (every word, title or company, no accents), company filter, newest-first sorting with first-seen as fallback, company counts, and "3 days ago" dates counted in Athens calendar days. Pay written as a range or one amount with its period, and the "only jobs that show pay" filter.
- `CompaniesPanel.test.tsx`: lists the companies with how each board is doing ("read today · 12 jobs", "failing since…", "not read yet") and counts the failing ones in the heading; adding and removing send the API key and reload the list; nothing is removed without confirming; a refused link shows the server's reason and keeps the text.
- `JobCard.test.tsx`: only `http(s)` addresses become links (never `javascript:`), the button for the current status is disabled, "posted"/"found" dates, and opening the link is reported. The pay shows with its sentence on hover, and not at all when the job does not say.

Integration tests (Testcontainers starts a throw-away `postgres:16-alpine`; the Flyway migrations build the schema):

- `JobTest`: a later read without a date keeps the date an earlier read gave. The same for pay.
- `PayParserTest`: pay in English and Greek (ranges, "30-40k", "1.200 €", em dashes, £, per hour), the amounts that are not pay (relocation bonus, meal vouchers, "€5 million", customers, revenue, insurance, "competitive salary" alone), the sentence kept and cut at whole words, Remotive\'s salary field, Lever\'s range, numbers written either way ("30.000", "30,000", "1.200,50", "35,3k").
- `TrackedCompanyRepositoryIT`: a Workday search and a SuccessFactors search can be tracked (migrations `V5`, `V6`); migration `V2` starts with the 16 companies, and the unique index refuses the same company with other capitals but allows the same name on another board.
- `JobRepositoryIT`: lookup by fingerprint, the UNIQUE constraint rejects a duplicate, the dashboard query hides SKIP/HIDDEN jobs and sorts best first, and leaves out jobs no board lists any more except ones he applied to; a job of a board that is failing stays while a board that works and dropped it closes it; the weekly email query keeps only new STRONG jobs he has not acted on that are still listed.
- `IngestServiceIT`: a full ingest in the real Spring context with stub job boards: real scoring from `application.yml`, the same job from two boards stored once, a refreshed score is really written to the database while his status is kept, a board that is down doesn't stop the others, and a week later a board that stayed down still has its job on the dashboard while a working board's dropped job is gone.

## Roadmap

- [x] React + TypeScript dashboard (replace `static/index.html`)
- [x] Testcontainers integration test for the repository and the full ingest
- [x] Add and remove tracked companies from the dashboard
- [x] Fetch full SmartRecruiters job descriptions for promising titles
- [x] Weekly email digest of new STRONG matches
- [ ] Optional LLM re-ranking of the top 20 jobs against the CV
