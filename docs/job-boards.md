# Job boards

You add a company by pasting its careers link in **Companies**. The board is recognised from the link
(`source/CareersLink`, which asks each source class's `link(Link)` in turn), and asked once whether the company exists,
so a typo is refused with a clear message instead of being saved. **remove** (after a confirmation) stops reading a
company; its jobs keep their statuses, and leave the dashboard after 3 days, except the ones you applied to.

## Workable, Greenhouse, Lever, SmartRecruiters

| Careers link | Board | Read through |
|---|---|---|
| `apply.workable.com/<slug>` or `<slug>.workable.com` | Workable | `apply.workable.com/api/v1/widget/accounts/<slug>?details=true` |
| `boards.greenhouse.io/<token>`, `job-boards.greenhouse.io/<token>` | Greenhouse | `boards-api.greenhouse.io/v1/boards/<token>/jobs?content=true` |
| `jobs.lever.co/<slug>` | Lever | `api.lever.co/v0/postings/<slug>?mode=json` |
| `careers.smartrecruiters.com/<CompanyId>` | SmartRecruiters | `api.smartrecruiters.com/v1/companies/<CompanyId>/postings` |

- Lever's API gives no company name, so the company is named from its link (`kaizen-gaming` → "Kaizen Gaming").
- Greenhouse jobs are dated by `first_published`, not `updated_at`, which changes whenever the ad is edited.
- SmartRecruiters answers an unknown company with an empty list instead of `404`, so a company with no jobs is
  logged as a warning, and its list has no descriptions (the full ad is read for promising jobs).
- A Workable account can exist and still list nothing: some companies keep an old account after moving their hiring
  elsewhere. Check the account lists jobs before adding it.

## Workday

Sites like `accenture.wd103.myworkdayjobs.com/AccentureCareers` list a company's jobs worldwide, so **filter the job
search to Greece on the company's site first** (for example *Location / Country*) and paste the link from the address
bar. The filters in it are kept (`locationCountry=566388c1…`), sorted, so the same search pasted twice is the same
company; tracking parameters are dropped. Some companies filter by office instead of country (PwC: Athens and
Thessaloniki, `locations=…&locations=…`).

Each fetch repeats that search through the JSON the site's own pages use (`/wday/cxs/<tenant>/<site>/jobs`), 20 per
page. A search of more than 500 jobs is refused, as a sign the link was not filtered. The list has only a title and a
rough date ("Posted 9 Days Ago"; "30+ Days Ago" gives none), so the full ad is read for promising jobs: description,
real posting date, and the offices, the accepted ones first. Workday gives no company name, so it comes from the link
(`pwc` → "PWC").

This search is not a documented API. If Workday changes it, the company turns red in **Companies** and its jobs stay.
Workday also limits how many requests one address may send: a burst of searches across companies gets `429`.

## SuccessFactors

Sites like `careers.ey.com`, `opportunities.vodafone.com` and `career.kotsovolos.gr` live on the company's own domain
and have no public API. A link is recognised by its shape, a path ending in `/search/`; adding it checks the page
really is a SuccessFactors search. **Search for Greece on the site** and paste the results link; the filters that
choose jobs (`q`, `locationsearch`, `locale`, `optionsFacetsDD_…`) are kept.

Each fetch reads the result pages themselves: the table layout (EY, Vodafone: 25 per page) and the card layout
(Kotsovolos: 30 per page), walked with `startrow` until the total is reached or a page brings nothing new. More than
500 jobs is refused as unfiltered. A lone country code is written out ("Athens, GR" → "Athens, Greece") so the place
filter recognises it. The job's own page gives the description and the posting date. The company is named from its
domain (`ey` → "EY").

Reading pages is the most fragile kind of source here: a redesign breaks it, which then shows in red in **Companies**
while its jobs stay.

## Remotive

Read once per run (`radar.sources.remotive: true`): its free API returns the same short list whatever the search term,
and its terms ask for at most four calls a day and a visible link back, which the dashboard shows as "via Remotive".
Only jobs open to Greece, Europe or worldwide are kept.

## The API

POST and DELETE need the API key.

```bash
curl localhost:8080/api/companies
curl -X POST localhost:8080/api/companies -H 'X-Api-Key: change-me' -H 'Content-Type: application/json' \
     -d '{"url":"https://apply.workable.com/learnworlds"}'
curl -X DELETE localhost:8080/api/companies/7 -H 'X-Api-Key: change-me'
```

Answers: `201` with the company and how many jobs it has open now; `400` for a link that is not a supported careers
page, or a company the board does not know; `409` if it is already tracked (capitals do not matter); `502` if the board
does not answer. The list also shows each board's health: `lastReadAt`, `jobs`, `failingSince`, `error`.

Other endpoints: `GET /api/jobs?status=NEW,SEEN` (grouped by bucket), `PATCH /api/jobs/{id}/status`,
`POST /api/ingest` (fetch now), `POST /api/digest` (send the weekly email now).
