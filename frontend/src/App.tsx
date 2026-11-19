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
import { ALL_SOURCES, Filters } from './components/Filters'
import { JobCard } from './components/JobCard'

const LABELS: Record<Bucket, string> = { STRONG: 'Strong match', GOOD: 'Good match', STRETCH: 'Stretch' }
const DEFAULT_STATUSES: JobStatus[] = ['NEW', 'SEEN', 'APPLIED']
const KEY_STORAGE = 'jobRadar.apiKey'
const NO_JOBS: GroupedJobs = { STRONG: [], GOOD: [], STRETCH: [] }

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
  const [source, setSource] = useState(ALL_SOURCES)
  const [loaded, setLoaded] = useState<GroupedJobs>(NO_JOBS)
  const [apiKey, setApiKey] = useState(readKey)
  const [message, setMessage] = useState('')
  const [fetching, setFetching] = useState(false)

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
      .then((result) => current && setLoaded(result))
      .catch((e: unknown) => current && setMessage(errorText(e)))
    return () => {
      current = false
    }
  }, [statuses, noneTicked, reloads])

  const sources = useMemo(
    () => [...new Set(BUCKETS.flatMap((b) => jobs[b].map((j) => j.source)))].sort(),
    [jobs],
  )

  async function changeStatus(job: Job, status: JobStatus) {
    try {
      await updateStatus(job.id, status, apiKey)
      reload()
    } catch (e) {
      setMessage(errorText(e))
    }
  }

  async function fetchNow() {
    setFetching(true)
    setMessage('Fetching…')
    try {
      const r = await ingestNow(apiKey)
      setMessage(`Fetched ${r.fetched}, ${r.inserted} new, ${r.updated} updated`)
      reload()
    } catch (e) {
      setMessage(errorText(e))
    } finally {
      setFetching(false)
    }
  }

  return (
    <>
      <header>
        <h1>Job Radar</h1>
        <span className="message" role="status">
          {message}
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
          sources={sources}
          source={source}
          onSourceChange={setSource}
        />
        <CompaniesPanel apiKey={apiKey} />
        {BUCKETS.map((bucket) => {
          const shown = jobs[bucket].filter((j) => source === ALL_SOURCES || j.source === source)
          return (
            <section key={bucket} aria-label={LABELS[bucket]}>
              <h2>
                {LABELS[bucket]} ({shown.length})
              </h2>
              {shown.length === 0 ? (
                <p className="empty">Nothing here yet.</p>
              ) : (
                shown.map((job) => <JobCard key={job.id} job={job} onStatus={changeStatus} />)
              )}
            </section>
          )
        })}
      </main>
    </>
  )
}
