import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Job } from '../api'
import { safeUrl } from '../safeUrl'
import { JobCard } from './JobCard'

const job: Job = {
  id: 1,
  source: 'workable',
  company: 'Acme',
  title: 'Junior Java Developer',
  location: 'Athens, Greece',
  remote: false,
  url: 'https://example.com/1',
  postedAt: null,
  score: 10,
  bucket: 'STRONG',
  reasons: '+3 java',
  status: 'SEEN',
  firstSeenAt: '2026-11-18T08:00:00Z',
  lastSeenAt: '2026-11-18T08:00:00Z',
}

describe('JobCard', () => {
  it('only turns http and https addresses into links', () => {
    expect(safeUrl('https://apply.workable.com/j/ABC')).toBe('https://apply.workable.com/j/ABC')
    expect(safeUrl('javascript:alert(1)')).toBeUndefined()
    expect(safeUrl('not a url')).toBeUndefined()
  })

  it('does not link a job whose address is not http(s)', () => {
    render(<JobCard job={{ ...job, url: 'javascript:alert(1)' }} onStatus={() => {}} />)

    expect(screen.getByText('Junior Java Developer')).not.toHaveAttribute('href')
  })

  it('disables the button for the status the job already has', () => {
    render(<JobCard job={job} onStatus={() => {}} />)

    expect(screen.getByRole('button', { name: 'seen' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'applied' })).toBeEnabled()
  })
})
