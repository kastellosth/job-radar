import { STATUSES, type JobStatus } from '../api'

export const ALL_SOURCES = ''

interface Props {
  statuses: readonly JobStatus[]
  onStatusesChange: (statuses: JobStatus[]) => void
  sources: readonly string[]
  source: string
  onSourceChange: (source: string) => void
}

export function Filters({ statuses, onStatusesChange, sources, source, onSourceChange }: Props) {
  function toggle(status: JobStatus, checked: boolean) {
    // Keep the fixed STATUSES order, so the request URL does not depend on click order.
    onStatusesChange(STATUSES.filter((s) => (s === status ? checked : statuses.includes(s))))
  }

  return (
    <div className="filters">
      <fieldset>
        <legend>Status</legend>
        {STATUSES.map((status) => (
          <label key={status}>
            <input
              type="checkbox"
              checked={statuses.includes(status)}
              onChange={(e) => toggle(status, e.target.checked)}
            />
            {status.toLowerCase()}
          </label>
        ))}
      </fieldset>
      <label>
        Source{' '}
        <select value={source} onChange={(e) => onSourceChange(e.target.value)}>
          <option value={ALL_SOURCES}>all sources</option>
          {sources.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
