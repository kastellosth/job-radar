import type { Job, JobStatus } from '../api'
import { jobDate, timeAgo } from '../jobView'
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
  const when = `${job.postedAt ? 'posted' : 'found'} ${timeAgo(jobDate(job))}`
  return (
    <article className={`job ${job.status}`} aria-label={job.title}>
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
        {job.status !== 'SEEN' && <span className="pill">{job.status}</span>}
      </div>
      <div className="score" title="score">
        {job.score}
      </div>
      {/* Remotive's terms ask to name them as the source and link to their job page. */}
      <div className="meta">
        {job.company} · {where} · via {SOURCE_NAMES[job.source] ?? job.source} ·{' '}
        <time dateTime={jobDate(job)}>{when}</time>
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
