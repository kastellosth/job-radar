import { BUCKETS, type GroupedJobs, type Job } from './api'

export const ALL = ''

export type SortOrder = 'best' | 'newest'

/** What the user narrowed the list to. Done in the page: the API stays unchanged. */
export interface ViewOptions {
  query: string
  company: string
  sort: SortOrder
}

export const DEFAULT_VIEW: ViewOptions = { query: '', company: ALL, sort: 'best' }

export interface CompanyCount {
  name: string
  count: number
}

/** Lower case without accents, so "αθηνα" finds "Αθήνα" and "qualco" finds "QUALCO". */
function plain(s: string): string {
  return s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

/** When the job was posted, or first seen when the board gives no date. */
export function jobDate(job: Job): string {
  return job.postedAt ?? job.firstSeenAt
}

/**
 * The jobs of one bucket that match the view. Every word of the search must appear in the title or company.
 * "best" keeps the API's order (score, then newest first).
 */
export function visibleJobs(jobs: readonly Job[], view: ViewOptions): Job[] {
  const words = plain(view.query).split(/\s+/).filter(Boolean)
  const shown = jobs.filter((job) => {
    if (view.company !== ALL && job.company !== view.company) return false
    const text = plain(`${job.title} ${job.company}`)
    return words.every((w) => text.includes(w))
  })
  if (view.sort === 'newest') {
    shown.sort((a, b) => Date.parse(jobDate(b)) - Date.parse(jobDate(a)))
  }
  return shown
}

/** Companies in the loaded jobs, most jobs first, for the company filter. */
export function companiesIn(jobs: GroupedJobs): CompanyCount[] {
  const counts = new Map<string, number>()
  for (const job of BUCKETS.flatMap((b) => jobs[b])) {
    counts.set(job.company, (counts.get(job.company) ?? 0) + 1)
  }
  return [...counts]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
}

// Athens time, so a job posted at 01:00 on the 16th does not show as the 15th.
const dateFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'Europe/Athens',
})

/** "16 Nov 2026" */
export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

const DAY = 24 * 60 * 60 * 1000
const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

/** "today", "yesterday", "3 days ago", "2 months ago". A date in the future (clock skew) counts as today. */
export function timeAgo(iso: string, now: number = Date.now()): string {
  const days = Math.floor((now - Date.parse(iso)) / DAY)
  if (days < 1) return 'today'
  if (days < 30) return relative.format(-days, 'day')
  if (days < 365) return relative.format(-Math.floor(days / 30), 'month')
  return relative.format(-Math.floor(days / 365), 'year')
}
