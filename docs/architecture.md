# How Job Radar works

```
Workable ────────┐
SmartRecruiters ─┤
Greenhouse ──────┤
Lever ───────────┼──> IngestService ──> dedupe ──> JobScorer ──> PostgreSQL ──> REST API ──> dashboard
Workday ─────────┤    (08:00 + 18:00 Athens)
SuccessFactors ──┤
Remotive ────────┘
```

The backend is one Spring Boot application in five packages; the dashboard is a React app built into the same jar, so
the page and the API are served from one place (no CORS, one deploy).

| Package | What it holds |
|---|---|
| `source/` | One class per job board: fetch, map into a common `JobPosting`, and recognise the board's links |
| `matching/` | `JobScorer` and `PayParser`: pure functions, no I/O |
| `service/` | `IngestService` (fetch → dedupe → score → save), `CompanyService`, the weekly email |
| `domain/` | JPA entities and repositories: `Job`, `TrackedCompany`, `BoardHealth` |
| `web/` | REST controllers, the API key filter, the `JobView` the API answers with |

## Fetching

`IngestService` runs at 08:00 and 18:00 (Athens), or when **Fetch now** is pressed.

1. Every source is asked for its boards. A company board source (`CompanyBoardSource`) reads which companies to fetch
   from the `tracked_company` table on every run, so a company added in the dashboard is read next time without a
   restart. Each board comes back as a `BoardRead`: its postings, or why it failed. One failing board never stops the
   others.
2. The boards are read **before** the database transaction opens, so a slow board never holds a connection.
3. Only one fetch runs at a time: pressing **Fetch now** during the scheduled run answers `409` instead of racing it
   (two runs inserting the same new job would make one fail).
4. In one transaction: each board's health is recorded, postings are deduplicated, scored, and inserted or refreshed.

**Deduplication.** The same job is often posted on several boards, or reposted with a new id. Company + title +
location, lower-cased with accents and punctuation removed, is its identity (`JobPosting.fingerprint`).

**Full ads.** Some boards list jobs without their description (SmartRecruiters, Workday, SuccessFactors). For those,
the full ad is read only for jobs that could score at all (accepted place, software title), at most 50 per company per
run, so a big company does not slow every run down (`CompanyBoardSource.readAds`).

## Scoring

`JobScorer` is pure and deterministic: same posting and same profile, same score.

- A job in a place you do not accept (and not remote) is skipped. Places are compared without accents, so "ΑΘΗΝΑ" in
  capitals matches "αθήνα".
- A title without a role word (developer, engineer, QA, DevOps, προγραμματιστής…) is skipped before any points, so
  "Junior Talent Acquisition Specialist" cannot ride on its "junior" boost.
- Then: points for skills anywhere in the ad, extra points for title words like "junior", penalties for "senior",
  "lead", "manager" **in the title only** (junior ads often mention "senior engineers" or "the engineering manager"),
  penalties for words like "German" anywhere, and −2 per year of required experience above 2.
- Every point is kept as a readable reason (`+3 java, +4 title:junior, -2 asks for 3+ years`) and shown on the card.

All weights, thresholds, accepted places and role words live in `application.yml`, so tuning needs no code change.

## Status, closed jobs and board health

A job is NEW → SEEN / APPLIED / REJECTED / HIDDEN. When it shows up again its score is refreshed and your status kept.
Opening a NEW job marks it SEEN, so NEW always means "not read yet".

Every read of every board is recorded in `board_health`: when it last worked, how many jobs it listed, and since when
it fails. A job is taken as **closed** only when its board has been read fine for 3 days (`hide-unlisted-after`)
without listing it; jobs you applied to always stay. So a board that is down or broken keeps its jobs instead of
emptying the dashboard. A board that suddenly lists no jobs after listing some counts as failing too, since that is far
more often a broken page or a wrong id than a company closing every opening at once. **Companies** shows each board as
"read today · 12 jobs" or, in red, "failing since 18 Nov: …".

## Pay

`PayParser` takes a board's pay field when it has one (Lever's `salaryRange`, Remotive's `salary`), and otherwise reads
the ad in English or Greek: "€30,000 - €40,000 gross per year", "30-40k EUR", "Μικτές μηνιαίες αποδοχές: 1.200 €".

An amount only counts when it has a currency, a pay word sits close by (salary, gross, net, μισθός, αποδοχές, αμοιβή,
μικτά, καθαρά…), no benefit word sits right next to it (bonus, meal voucher, relocation, επίδομα…), and it is plausible
for its period (a month of 300–40,000, a year of 5,000–600,000; without a period word, five figures are a year). Missing
a stated salary is preferred to showing a relocation bonus as pay. Nothing is converted, and the sentence it came from
is kept, so gross or net and "14 salaries" can be read as written.

## Dashboard

React + TypeScript, built by Vite into `target/classes/static`. Jobs are grouped by bucket; search by title or company
(accents and capitals ignored), filter by company, status, or to the jobs that show pay; sort by best match or newest.
Cards show the posting date and how long ago (both in Athens calendar days), the pay with its sentence on hover, and
the score's reasons. An empty bucket says whether it is loading, the server failed, or the filters hid its jobs.

## Security

- Reading is open; writes under `/api` need the `X-Api-Key` header (`web/ApiKeyFilter`), compared in constant time.
  One shared secret for one user needs no users, sessions or login page, so there is no Spring Security.
- Links from job boards only become links when they are `http(s)` (never `javascript:`), on the dashboard and in the
  email; text from job boards is escaped in the email.
- A pasted SuccessFactors link must be a public domain name (no IP address or `localhost`), so the server is not
  pointed at internal addresses.
- Mail server errors go to the log, not to the API answer, because they can contain the account.

## What is not read, and why

LinkedIn, Glassdoor, Indeed and similar sites are not read: their terms forbid it. The boards that are read are the
companies' own job boards, through their public APIs, or, for Workday and SuccessFactors, the same requests their
public career pages make, within `robots.txt` (the SuccessFactors RSS feed under `/services/` is disallowed, and
ignores the location anyway). Requests are few: two runs a day, at most 50 full ads per company per run.
