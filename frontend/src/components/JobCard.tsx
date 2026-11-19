import type { Job, JobStatus } from '../api'
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
}

export function JobCard({ job, onStatus }: Props) {
  const where = job.remote ? 'Remote' : (job.location ?? '')
  return (
    <article className={`job ${job.status}`} aria-label={job.title}>
      <div className="title">
        <a href={safeUrl(job.url)} target="_blank" rel="noopener noreferrer">
          {job.title}
        </a>
        {job.status !== 'SEEN' && <span className="pill">{job.status}</span>}
      </div>
      <div className="score" title="score">
        {job.score}
      </div>
      {/* Remotive's terms ask to name them as the source and link to their job page. */}
      <div className="meta">
        {job.company} · {where} · via {SOURCE_NAMES[job.source] ?? job.source}
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
