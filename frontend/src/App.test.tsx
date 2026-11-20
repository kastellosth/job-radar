import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import type { GroupedJobs, Job } from './api'

function job(overrides: Partial<Job>): Job {
  return {
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
    reasons: '+3 java, +4 title:junior',
    status: 'NEW',
    firstSeenAt: '2026-11-18T08:00:00Z',
    lastSeenAt: '2026-11-18T08:00:00Z',
    ...overrides,
  }
}

const JOBS: GroupedJobs = {
  STRONG: [job({ id: 1 })],
  GOOD: [
    job({ id: 2, title: 'Backend Developer', company: 'Remote Co', score: 6, bucket: 'GOOD', source: 'remotive', remote: true }),
  ],
  STRETCH: [],
}

interface Call {
  method: string
  url: string
  init?: RequestInit
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

/** A fake server: records every request and answers by "METHOD /path". */
function fakeApi(handlers: Record<string, () => Response>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      calls.push({ method, url, init })
      const handler = handlers[`${method} ${url.split('?')[0]}`]
      return handler ? handler() : json({ status: 404 }, 404)
    }),
  )
  return calls
}

const gets = (calls: Call[]) => calls.filter((c) => c.method === 'GET' && c.url.startsWith('/api/jobs'))
const section = (name: string) => screen.getByRole('region', { name })

describe('App', () => {
  it('shows the jobs grouped by bucket, best groups first', async () => {
    fakeApi({ 'GET /api/jobs': () => json(JOBS) })

    render(<App />)

    expect(await screen.findByRole('article', { name: 'Junior Java Developer' })).toBeInTheDocument()
    expect(within(section('Strong match')).getByRole('heading')).toHaveTextContent('Strong match (1)')
    expect(within(section('Good match')).getByText(/Remote · via Remotive/)).toBeInTheDocument()
    expect(within(section('Stretch')).getByText('Nothing here yet.')).toBeInTheDocument()
  })

  it('asks the API for the statuses that are ticked, and asks nothing when none is', async () => {
    const calls = fakeApi({ 'GET /api/jobs': () => json(JOBS) })
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })
    expect(gets(calls)[0].url).toBe('/api/jobs?status=NEW,SEEN,APPLIED')

    await userEvent.click(screen.getByRole('checkbox', { name: 'rejected' }))
    await waitFor(() => expect(gets(calls)).toHaveLength(2))
    expect(gets(calls)[1].url).toBe('/api/jobs?status=NEW,SEEN,APPLIED,REJECTED')

    for (const name of ['new', 'seen', 'applied', 'rejected']) {
      await userEvent.click(screen.getByRole('checkbox', { name }))
    }
    expect(screen.queryAllByRole('article')).toHaveLength(0)
    expect(gets(calls).at(-1)?.url).not.toBe('/api/jobs?status=')
  })

  it('filters by company and by search text in the page, without calling the API again', async () => {
    const calls = fakeApi({ 'GET /api/jobs': () => json(JOBS) })
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })

    await userEvent.selectOptions(screen.getByRole('combobox', { name: /company/i }), 'Remote Co')
    expect(screen.queryByRole('article', { name: 'Junior Java Developer' })).not.toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'Backend Developer' })).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByRole('combobox', { name: /company/i }), '')
    await userEvent.type(screen.getByRole('searchbox', { name: 'Search' }), 'junior')
    expect(screen.getByRole('article', { name: 'Junior Java Developer' })).toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Backend Developer' })).not.toBeInTheDocument()
    expect(within(section('Strong match')).getByRole('heading')).toHaveTextContent('Strong match (1)')
    expect(within(section('Good match')).getByRole('heading')).toHaveTextContent('Good match (0)')

    expect(gets(calls)).toHaveLength(1)
  })

  it('says a bucket emptied by the search has no matches, unlike a bucket with no jobs at all', async () => {
    fakeApi({ 'GET /api/jobs': () => json(JOBS) })
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search' }), 'nothing like this')

    expect(within(section('Strong match')).getByText('No jobs here match your filters.')).toBeInTheDocument()
    expect(within(section('Stretch')).getByText('Nothing here yet.')).toBeInTheDocument()
  })

  it('says it is loading until the jobs arrive', async () => {
    fakeApi({ 'GET /api/jobs': () => json(JOBS) })
    render(<App />)

    expect(within(section('Stretch')).getByText('Loading…')).toBeInTheDocument()
    expect(await within(section('Stretch')).findByText('Nothing here yet.')).toBeInTheDocument()
  })

  it('says the jobs could not be loaded, instead of "nothing here", when the server fails', async () => {
    fakeApi({ 'GET /api/jobs': () => json({ detail: 'Database is down' }, 500) })
    render(<App />)

    expect(await within(section('Strong match')).findByText('Could not load the jobs.')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Database is down')

    await userEvent.click(screen.getByRole('button', { name: 'Close message' }))
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('lets the fetch report fade after a few seconds, but keeps errors', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    let ingestAnswer = json({ fetched: 10, unique: 10, inserted: 1, updated: 9 })
    fakeApi({
      'GET /api/jobs': () => json(JOBS),
      'POST /api/ingest': () => ingestAnswer,
    })
    localStorage.setItem('jobRadar.apiKey', 's3cret')
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })

    await user.click(screen.getByRole('button', { name: 'Fetch now' }))
    expect(await screen.findByText('Fetched 10, 1 new, 9 updated')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(8000))
    expect(screen.getByRole('status')).toBeEmptyDOMElement()

    ingestAnswer = json({ detail: 'A fetch is already running.' }, 409)
    await user.click(screen.getByRole('button', { name: 'Fetch now' }))
    expect(await screen.findByText('A fetch is already running.')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(60_000))
    expect(screen.getByRole('status')).toHaveTextContent('A fetch is already running.')
  })

  it('sends a status change with the API key, remembers the key, and reloads the list', async () => {
    const calls = fakeApi({
      'GET /api/jobs': () => json(JOBS),
      'PATCH /api/jobs/1/status': () => json(job({ status: 'APPLIED' })),
    })
    render(<App />)
    const card = await screen.findByRole('article', { name: 'Junior Java Developer' })

    await userEvent.type(screen.getByLabelText('API key'), 's3cret')
    await userEvent.click(within(card).getByRole('button', { name: 'applied' }))

    await waitFor(() => expect(gets(calls)).toHaveLength(2))
    const patch = calls.find((c) => c.method === 'PATCH')
    expect(patch?.init?.headers).toMatchObject({ 'X-Api-Key': 's3cret' })
    expect(patch?.init?.body).toBe('{"status":"APPLIED"}')
    expect(localStorage.getItem('jobRadar.apiKey')).toBe('s3cret')
  })

  it('runs an ingest on "Fetch now" and reports what it found', async () => {
    const calls = fakeApi({
      'GET /api/jobs': () => json(JOBS),
      'POST /api/ingest': () => json({ fetched: 189, unique: 154, inserted: 3, updated: 151 }),
    })
    localStorage.setItem('jobRadar.apiKey', 's3cret')
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })

    await userEvent.click(screen.getByRole('button', { name: 'Fetch now' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Fetched 189, 3 new, 151 updated')
    expect(calls.find((c) => c.method === 'POST')?.init?.headers).toMatchObject({ 'X-Api-Key': 's3cret' })
    await waitFor(() => expect(gets(calls)).toHaveLength(2))
  })

  it('points at the API key field when the server answers 401', async () => {
    fakeApi({
      'GET /api/jobs': () => json(JOBS),
      'POST /api/ingest': () => json({ status: 401 }, 401),
    })
    render(<App />)
    await screen.findByRole('article', { name: 'Junior Java Developer' })

    await userEvent.click(screen.getByRole('button', { name: 'Fetch now' }))

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('Missing or wrong API key: enter it at the top.'),
    )
  })

  it('marks a NEW job SEEN when its link is opened, without waiting for the server', async () => {
    const calls = fakeApi({
      'GET /api/jobs': () => json(JOBS),
      'PATCH /api/jobs/1/status': () => json(job({ status: 'SEEN' })),
    })
    localStorage.setItem('jobRadar.apiKey', 's3cret')
    render(<App />)
    const card = await screen.findByRole('article', { name: 'Junior Java Developer' })
    expect(within(card).getByText('NEW')).toBeInTheDocument()

    const link = within(card).getByRole('link', { name: 'Junior Java Developer' })
    link.addEventListener('click', (e) => e.preventDefault()) // jsdom cannot open tabs
    await userEvent.click(link)

    expect(within(card).queryByText('NEW')).not.toBeInTheDocument()
    await waitFor(() => expect(calls.find((c) => c.method === 'PATCH')?.init?.body).toBe('{"status":"SEEN"}'))
    expect(calls.find((c) => c.method === 'PATCH')?.init?.headers).toMatchObject({ 'X-Api-Key': 's3cret' })
  })

  const openLink = async (name: string) => {
    const link = screen.getByRole('link', { name })
    link.addEventListener('click', (e) => e.preventDefault()) // jsdom cannot open tabs
    await userEvent.click(link)
  }

  it('sends nothing when a job that is not NEW is opened', async () => {
    const calls = fakeApi({
      'GET /api/jobs': () => json({ ...JOBS, STRONG: [job({ id: 3, title: 'Seen Job', status: 'SEEN' })] }),
    })
    localStorage.setItem('jobRadar.apiKey', 's3cret')
    render(<App />)
    await screen.findByRole('article', { name: 'Seen Job' })

    await openLink('Seen Job')

    expect(calls.filter((c) => c.method === 'PATCH')).toHaveLength(0)
  })

  it('sends nothing and keeps the job NEW when there is no API key', async () => {
    const calls = fakeApi({ 'GET /api/jobs': () => json(JOBS) })
    render(<App />)
    const card = await screen.findByRole('article', { name: 'Junior Java Developer' })

    await openLink('Junior Java Developer')

    expect(calls.filter((c) => c.method === 'PATCH')).toHaveLength(0)
    expect(within(card).getByText('NEW')).toBeInTheDocument()
  })
})
