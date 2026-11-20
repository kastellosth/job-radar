import type { Job, JobStatus } from '../api'
import { formatDate, jobDate, timeAgo } from '../jobView'
import { safeUrl } from '../safeUrl'

const ACTIONS: JobStatus[] = ['SEEN', 'APPLIED', 'REJECTED', 'HIDDEN']

const SOURCE_NAMES: Record<string, string> = {
  workable: 'Workable',
  smartrecruiters: 'SmartRecruiters',
  greenhouse: 'Greenhouse',
  lever: 'Lever',
  remotive: 'Remotive',
}

interface Props {
  job: Job
  onStatus: (job: Job, status: JobStatus) => void
  /** Called when the job's link is opened (click, middle click or Enter). */
  onOpen: (job: Job) => void
}

export function JobCard({ job, onStatus, onOpen }: Props) {
  const where = job.remote ? 'Remote' : (job.location ?? '')
  const date = jobDate(job)
  return (
    <article className={`job bucket-${job.bucket} ${job.status}`} aria-label={job.title}>
      <div className="title">
        <a
          href={safeUrl(job.url)}
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => onOpen(job)}
          onAuxClick={() => onOpen(job)}
        >
          {job.title}
        </a>
        {job.status !== 'SEEN' && <span className={`pill ${job.status}`}>{job.status}</span>}
      </div>
      <div className="score" title="match score">
        {job.score}
      </div>
      {/* Remotive's terms ask to name them as the source and link to their job page. */}
      <div className="meta">
        <strong>{job.company}</strong> · {where} · via {SOURCE_NAMES[job.source] ?? job.source}
      </div>
      <div className="date">
        {/* Without a date from the board, the day the radar first saw the job is the best we know. */}
        {job.postedAt ? 'Posted' : 'Found'} <time dateTime={date}>{formatDate(date)}</time> · {timeAgo(date)}
      </div>
      {job.reasons && <div className="reasons">{job.reasons}</div>}
      <div className="actions">
        {ACTIONS.map((status) => (
          <button
            key={status}
            type="button"
            disabled={job.status === status}
            onClick={() => onStatus(job, status)}
          >
            {status.toLowerCase()}
          </button>
        ))}
      </div>
    </article>
  )
}
