import { describe, expect, it } from 'vitest'
import type { Job } from './api'
import { ALL, DEFAULT_VIEW, companiesIn, formatDate, timeAgo, visibleJobs } from './jobView'

function job(id: number, title: string, company: string, postedAt: string | null, firstSeenAt = '2026-11-01T00:00:00Z'): Job {
  return {
    id, title, company, postedAt, firstSeenAt,
    source: 'workable', location: 'Athens', remote: false, url: 'https://x', score: 10, bucket: 'STRONG',
    reasons: null, status: 'NEW', lastSeenAt: firstSeenAt,
  }
}

const JOBS = [
  job(1, 'Junior Java Developer', 'European Dynamics', '2026-11-10T00:00:00Z'),
  job(2, 'Προγραμματιστής Java', 'QUALCO Group', null, '2026-11-18T00:00:00Z'),
  job(3, 'React Software Engineer', 'Agile Actors', '2026-11-15T00:00:00Z'),
]

const titles = (jobs: Job[]) => jobs.map((j) => j.id)

describe('visibleJobs', () => {
  it('keeps the API order and every job by default', () => {
    expect(titles(visibleJobs(JOBS, DEFAULT_VIEW))).toEqual([1, 2, 3])
  })

  it('finds every word of the search in the title or the company, ignoring case and accents', () => {
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, query: 'java' }))).toEqual([1, 2])
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, query: 'qualco' }))).toEqual([2])
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, query: 'προγραμματιστης' }))).toEqual([2])
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, query: '  java   dynamics ' }))).toEqual([1])
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, query: 'python' }))).toEqual([])
  })

  it('shows one company only', () => {
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, company: 'Agile Actors' }))).toEqual([3])
  })

  it('sorts newest first by posting date, or by first seen when there is none', () => {
    expect(titles(visibleJobs(JOBS, { ...DEFAULT_VIEW, sort: 'newest' }))).toEqual([2, 3, 1])
  })

  it('does not change the list it was given', () => {
    const copy = [...JOBS]
    visibleJobs(JOBS, { query: '', company: ALL, sort: 'newest' })
    expect(JOBS).toEqual(copy)
  })
})

describe('companiesIn', () => {
  it('counts jobs per company across buckets, most first', () => {
    const grouped = { STRONG: [JOBS[0], JOBS[2]], GOOD: [job(4, 'QA', 'Agile Actors', null)], STRETCH: [] }

    expect(companiesIn(grouped)).toEqual([
      { name: 'Agile Actors', count: 2 },
      { name: 'European Dynamics', count: 1 },
    ])
  })
})

describe('timeAgo', () => {
  const now = Date.parse('2026-11-19T12:00:00Z')

  it.each([
    ['2026-11-19T08:00:00Z', 'today'],
    ['2026-11-18T10:00:00Z', 'yesterday'],
    ['2026-11-16T12:00:00Z', '3 days ago'],
    ['2026-09-10T12:00:00Z', '2 months ago'],
    ['2024-11-01T12:00:00Z', '2 years ago'],
    ['2026-11-20T12:00:00Z', 'today'],
  ])('%s is "%s"', (iso, expected) => {
    expect(timeAgo(iso, now)).toBe(expected)
  })
})

describe('formatDate', () => {
  it('writes the day in Athens time', () => {
    expect(formatDate('2026-11-16T09:00:00Z')).toBe('16 Nov 2026')
    expect(formatDate('2026-11-15T22:30:00Z')).toBe('16 Nov 2026')
  })
})
