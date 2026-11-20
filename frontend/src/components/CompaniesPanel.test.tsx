import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Company } from '../api'
import { CompaniesPanel } from './CompaniesPanel'

const LEARNWORLDS: Company = {
  id: 7,
  provider: 'Workable',
  slug: 'learnworlds',
  name: 'LearnWorlds',
  lastReadAt: new Date().toISOString(),
  jobs: 12,
  failingSince: null,
  error: null,
}
const EY: Company = {
  id: 8,
  provider: 'SuccessFactors',
  slug: 'ey',
  name: 'EY',
  lastReadAt: '2026-11-16T06:00:00Z',
  jobs: 39,
  failingSince: '2026-11-18T06:00:00Z',
  error: 'listed no jobs, after 39 last time',
}
const NEW_ONE: Company = { ...LEARNWORLDS, id: 9, name: 'Novibet', lastReadAt: null, jobs: null }

interface Call {
  method: string
  url: string
  init?: RequestInit
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function fakeApi(handlers: Record<string, () => Response>): Call[] {
  const calls: Call[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      calls.push({ method, url, init })
      const handler = handlers[`${method} ${url}`]
      return handler ? handler() : json({ status: 404 }, 404)
    }),
  )
  return calls
}

const listCalls = (calls: Call[]) => calls.filter((c) => c.method === 'GET')

describe('CompaniesPanel', () => {
  it('lists the tracked companies with their job board', async () => {
    fakeApi({ 'GET /api/companies': () => json([LEARNWORLDS]) })

    render(<CompaniesPanel apiKey="s3cret" />)

    expect(await screen.findByText('Companies (1)')).toBeInTheDocument()
    expect(screen.getByText('LearnWorlds')).toBeInTheDocument()
    expect(screen.getByText('· Workable')).toBeInTheDocument()
  })

  it('adds a company from its careers link with the API key, then reloads the list', async () => {
    const calls = fakeApi({
      'GET /api/companies': () => json([]),
      'POST /api/companies': () => json({ company: LEARNWORLDS, openJobs: 9 }, 201),
    })
    render(<CompaniesPanel apiKey="s3cret" />)
    await screen.findByText('Companies (0)')

    await userEvent.type(screen.getByLabelText('Careers page link'), 'https://apply.workable.com/learnworlds')
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('note')).toHaveTextContent('Added LearnWorlds: 9 open jobs now.')
    const post = calls.find((c) => c.method === 'POST')
    expect(post?.init?.headers).toMatchObject({ 'X-Api-Key': 's3cret' })
    expect(post?.init?.body).toBe('{"url":"https://apply.workable.com/learnworlds"}')
    await waitFor(() => expect(listCalls(calls)).toHaveLength(2))
    expect(screen.getByLabelText('Careers page link')).toHaveValue('')
  })

  it("shows the server's reason when a link is refused", async () => {
    fakeApi({
      'GET /api/companies': () => json([]),
      'POST /api/companies': () => json({ status: 400, detail: "Workable has no company 'nope'" }, 400),
    })
    render(<CompaniesPanel apiKey="s3cret" />)
    await screen.findByText('Companies (0)')

    await userEvent.type(screen.getByLabelText('Careers page link'), 'https://apply.workable.com/nope')
    await userEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('note')).toHaveTextContent("Workable has no company 'nope'")
    expect(screen.getByLabelText('Careers page link')).toHaveValue('https://apply.workable.com/nope')
  })

  it('removes a company with the API key, then reloads the list', async () => {
    const calls = fakeApi({
      'GET /api/companies': () => json([LEARNWORLDS]),
      'DELETE /api/companies/7': () => new Response(null, { status: 204 }),
    })
    render(<CompaniesPanel apiKey="s3cret" />)
    await screen.findByText('LearnWorlds')

    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await userEvent.click(screen.getByRole('button', { name: 'Remove LearnWorlds' }))

    expect(window.confirm).toHaveBeenCalledWith('Stop following LearnWorlds? Its board will not be read any more.')
    expect(await screen.findByRole('note')).toHaveTextContent('Removed LearnWorlds.')
    expect(calls.find((c) => c.method === 'DELETE')?.init?.headers).toMatchObject({ 'X-Api-Key': 's3cret' })
    await waitFor(() => expect(listCalls(calls)).toHaveLength(2))
  })

  it('keeps the company when the removal is not confirmed', async () => {
    const calls = fakeApi({ 'GET /api/companies': () => json([LEARNWORLDS]) })
    render(<CompaniesPanel apiKey="s3cret" />)
    await screen.findByText('LearnWorlds')

    vi.spyOn(window, 'confirm').mockReturnValue(false)
    await userEvent.click(screen.getByRole('button', { name: 'Remove LearnWorlds' }))

    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
    expect(screen.getByText('LearnWorlds')).toBeInTheDocument()
  })

  it("shows how each company's board is doing, and counts the failing ones in the heading", async () => {
    fakeApi({ 'GET /api/companies': () => json([LEARNWORLDS, EY, NEW_ONE]) })
    render(<CompaniesPanel apiKey="s3cret" />)
    await screen.findByText('LearnWorlds')

    expect(screen.getByText('Companies (3)')).toBeInTheDocument()
    expect(screen.getByText('· 1 failing')).toBeInTheDocument()
    expect(screen.getByText('read today · 12 jobs')).toBeInTheDocument()
    expect(
      screen.getByText('failing since 18 Nov 2026: listed no jobs, after 39 last time · its 39 jobs stay listed'),
    ).toBeInTheDocument()
    expect(screen.getByText('not read yet')).toBeInTheDocument()
  })
})
