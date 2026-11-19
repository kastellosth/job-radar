import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
  postedAt: '2026-11-16T09:00:00Z',
  score: 10,
  bucket: 'STRONG',
  reasons: '+3 java',
  status: 'SEEN',
  firstSeenAt: '2026-11-18T08:00:00Z',
  lastSeenAt: '2026-11-18T08:00:00Z',
}

const noop = () => {}

describe('JobCard', () => {
  beforeEach(() => {
    // Only Date is faked, so user events still run on real timers.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-19T12:00:00Z'))
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('only turns http and https addresses into links', () => {
    expect(safeUrl('https://apply.workable.com/j/ABC')).toBe('https://apply.workable.com/j/ABC')
    expect(safeUrl('javascript:alert(1)')).toBeUndefined()
    expect(safeUrl('not a url')).toBeUndefined()
  })

  it('does not link a job whose address is not http(s)', () => {
    render(<JobCard job={{ ...job, url: 'javascript:alert(1)' }} onStatus={noop} onOpen={noop} />)

    expect(screen.getByText('Junior Java Developer')).not.toHaveAttribute('href')
  })

  it('disables the button for the status the job already has', () => {
    render(<JobCard job={job} onStatus={noop} onOpen={noop} />)

    expect(screen.getByRole('button', { name: 'seen' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'applied' })).toBeEnabled()
  })

  it('says when the job was posted, or when it was found if the board gives no date', () => {
    const { rerender } = render(<JobCard job={job} onStatus={noop} onOpen={noop} />)
    expect(screen.getByText('posted 3 days ago')).toBeInTheDocument()

    rerender(<JobCard job={{ ...job, postedAt: null }} onStatus={noop} onOpen={noop} />)
    expect(screen.getByText('found yesterday')).toBeInTheDocument()
  })

  it('reports when its link is opened', async () => {
    const onOpen = vi.fn()
    render(<JobCard job={job} onStatus={noop} onOpen={onOpen} />)
    const link = screen.getByRole('link', { name: 'Junior Java Developer' })
    link.addEventListener('click', (e) => e.preventDefault()) // jsdom cannot open tabs

    await userEvent.click(link)

    expect(onOpen).toHaveBeenCalledWith(job)
  })
})
