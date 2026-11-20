import { useEffect, useMemo, useState } from 'react'
import {
  BUCKETS,
  errorText,
  fetchJobs,
  ingestNow,
  updateStatus,
  type Bucket,
  type GroupedJobs,
  type Job,
  type JobStatus,
} from './api'
import { CompaniesPanel } from './components/CompaniesPanel'
import { Filters } from './components/Filters'
import { JobCard } from './components/JobCard'
import { DEFAULT_VIEW, companiesIn, visibleJobs, type ViewOptions } from './jobView'

const LABELS: Record<Bucket, string> = { STRONG: 'Strong match', GOOD: 'Good match', STRETCH: 'Stretch' }
const DEFAULT_STATUSES: JobStatus[] = ['NEW', 'SEEN', 'APPLIED']
const KEY_STORAGE = 'jobRadar.apiKey'
const NO_JOBS: GroupedJobs = { STRONG: [], GOOD: [], STRETCH: [] }
/** How long a message like "Fetched 120, 3 new" stays at the top. Errors stay until closed or replaced. */
const NOTICE_MS = 8000

/** The first load decides what an empty bucket means: still loading, server down, or really empty. */
type FirstLoad = 'loading' | 'done' | 'failed'

interface Notice {
  text: string
  error: boolean
}

/** What an empty bucket says, so "nothing here" is only shown when it is true. */
function emptyText(noneTicked: boolean, firstLoad: FirstLoad, loadedInBucket: number): string {
  if (noneTicked) return 'Tick a status to see jobs.'
  if (firstLoad === 'loading') return 'Loading…'
  if (firstLoad === 'failed') return 'Could not load the jobs.'
  if (loadedInBucket > 0) return 'No jobs here match your filters.'
  return 'Nothing here yet.'
}

// Storage can be blocked (private mode, strict settings); the page must still work without it.
function readKey(): string {
  try {
    return localStorage.getItem(KEY_STORAGE) ?? ''
  } catch {
    return ''
  }
}

function saveKey(key: string) {
  try {
    localStorage.setItem(KEY_STORAGE, key)
  } catch {
    // only a convenience
  }
}

export default function App() {
  const [statuses, setStatuses] = useState<JobStatus[]>(DEFAULT_STATUSES)
  const [view, setView] = useState<ViewOptions>(DEFAULT_VIEW)
  const [loaded, setLoaded] = useState<GroupedJobs>(NO_JOBS)
  const [apiKey, setApiKey] = useState(readKey)
  const [notice, setNotice] = useState<Notice | null>(null)
  const [fetching, setFetching] = useState(false)
  const [firstLoad, setFirstLoad] = useState<FirstLoad>('loading')

  const say = (text: string) => setNotice({ text, error: false })
  const fail = (e: unknown) => setNotice({ text: errorText(e), error: true })

  useEffect(() => {
    if (!notice || notice.error || fetching) return
    const timer = setTimeout(() => setNotice(null), NOTICE_MS)
    return () => clearTimeout(timer)
  }, [notice, fetching])

  // The API treats "no status" as its default set, so an empty choice is answered here instead.
  const noneTicked = statuses.length === 0
  const jobs = noneTicked ? NO_JOBS : loaded

  // Bumped after every change, so the list is fetched again.
  const [reloads, setReloads] = useState(0)
  const reload = () => setReloads((n) => n + 1)

  useEffect(() => {
    if (noneTicked) return
    // A slow answer for an old filter must not overwrite the answer for the current one.
    let current = true
    fetchJobs(statuses)
      .then((result) => {
        if (!current) return
        setLoaded(result)
        setFirstLoad('done')
      })
      .catch((e: unknown) => {
        if (!current) return
        fail(e)
        setFirstLoad((state) => (state === 'loading' ? 'failed' : state))
      })
    return () => {
      current = false
    }
  }, [statuses, noneTicked, reloads])

  const companies = useMemo(() => companiesIn(jobs), [jobs])

  async function changeStatus(job: Job, status: JobStatus) {
    try {
      await updateStatus(job.id, status, apiKey)
      reload()
    } catch (e) {
      fail(e)
    }
  }

  /**
   * Opening a NEW job marks it SEEN, so NEW keeps meaning "not read yet". The card changes at once;
   * the request runs in the background. Without a key there is nothing to send, so nothing happens.
   */
  function markOpened(job: Job) {
    if (job.status !== 'NEW' || !apiKey) return
    setLoaded((current) => ({
      ...current,
      [job.bucket]: current[job.bucket].map((j) => (j.id === job.id ? { ...j, status: 'SEEN' as const } : j)),
    }))
    updateStatus(job.id, 'SEEN', apiKey).catch((e: unknown) => {
      fail(e)
      reload()
    })
  }

  async function fetchNow() {
    setFetching(true)
    say('Fetching…')
    try {
      const r = await ingestNow(apiKey)
      say(`Fetched ${r.fetched}, ${r.inserted} new, ${r.updated} updated`)
      reload()
    } catch (e) {
      fail(e)
    } finally {
      setFetching(false)
    }
  }

  return (
    <>
      <header>
        <h1>Job Radar</h1>
        <span className={notice?.error ? 'message error' : 'message'} role="status">
          {notice?.text}
          {notice?.error && (
            <button type="button" className="dismiss" aria-label="Close message" onClick={() => setNotice(null)}>
              ×
            </button>
          )}
        </span>
        <span className="spacer" />
        <label>
          API key{' '}
          <input
            type="password"
            value={apiKey}
            autoComplete="off"
            onChange={(e) => {
              setApiKey(e.target.value)
              saveKey(e.target.value)
            }}
          />
        </label>
        <button type="button" className="primary" onClick={fetchNow} disabled={fetching}>
          Fetch now
        </button>
      </header>
      <main>
        <Filters
          statuses={statuses}
          onStatusesChange={setStatuses}
          view={view}
          onViewChange={setView}
          companies={companies}
        />
        <CompaniesPanel apiKey={apiKey} />
        {BUCKETS.map((bucket) => {
          const shown = visibleJobs(jobs[bucket], view)
          return (
            <section key={bucket} className={`bucket bucket-${bucket}`} aria-label={LABELS[bucket]}>
              <h2>
                {LABELS[bucket]} ({shown.length})
              </h2>
              {shown.length === 0 ? (
                <p className="empty">{emptyText(noneTicked, firstLoad, jobs[bucket].length)}</p>
              ) : (
                shown.map((job) => (
                  <JobCard key={job.id} job={job} onStatus={changeStatus} onOpen={markOpened} />
                ))
              )}
            </section>
          )
        })}
      </main>
    </>
  )
}
