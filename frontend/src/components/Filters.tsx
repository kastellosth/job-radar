import { STATUSES, type JobStatus } from '../api'
import { ALL, type CompanyCount, type SortOrder, type ViewOptions } from '../jobView'

interface Props {
  statuses: readonly JobStatus[]
  onStatusesChange: (statuses: JobStatus[]) => void
  view: ViewOptions
  onViewChange: (view: ViewOptions) => void
  companies: readonly CompanyCount[]
}

export function Filters({ statuses, onStatusesChange, view, onViewChange, companies }: Props) {
  function toggle(status: JobStatus, checked: boolean) {
    // Keep the fixed STATUSES order, so the request URL does not depend on click order.
    onStatusesChange(STATUSES.filter((s) => (s === status ? checked : statuses.includes(s))))
  }

  // A chosen company stays in the list even when a status change leaves it with no jobs.
  const options =
    view.company === ALL || companies.some((c) => c.name === view.company)
      ? companies
      : [...companies, { name: view.company, count: 0 }]

  return (
    <div className="filters">
      <input
        type="search"
        className="search"
        placeholder="Search title or company"
        aria-label="Search"
        value={view.query}
        onChange={(e) => onViewChange({ ...view, query: e.target.value })}
      />
      <label>
        Company{' '}
        <select value={view.company} onChange={(e) => onViewChange({ ...view, company: e.target.value })}>
          <option value={ALL}>all companies</option>
          {options.map((c) => (
            <option key={c.name} value={c.name}>
              {c.name} ({c.count})
            </option>
          ))}
        </select>
      </label>
      <label>
        Sort{' '}
        <select value={view.sort} onChange={(e) => onViewChange({ ...view, sort: e.target.value as SortOrder })}>
          <option value="best">best match</option>
          <option value="newest">newest</option>
        </select>
      </label>
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
    </div>
  )
}
