export const BUCKETS = ['STRONG', 'GOOD', 'STRETCH'] as const
export type Bucket = (typeof BUCKETS)[number]

export const STATUSES = ['NEW', 'SEEN', 'APPLIED', 'REJECTED', 'HIDDEN'] as const
export type JobStatus = (typeof STATUSES)[number]

/** One job as GET /api/jobs returns it. */
export interface Job {
  id: number
  source: string
  company: string
  title: string
  location: string | null
  remote: boolean
  url: string
  postedAt: string | null
  score: number
  bucket: Bucket
  reasons: string | null
  status: JobStatus
  firstSeenAt: string
  lastSeenAt: string
}

export type GroupedJobs = Record<Bucket, Job[]>

export interface IngestReport {
  fetched: number
  unique: number
  inserted: number
  updated: number
}

/** A 401, kept apart from other errors so the page can point at the API key field. */
export class UnauthorizedError extends Error {}

/** A message for the user, whatever was thrown. */
export function errorText(e: unknown): string {
  if (e instanceof UnauthorizedError) return 'Missing or wrong API key: enter it at the top.'
  return e instanceof Error ? e.message : String(e)
}

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (res.status === 401) {
    throw new UnauthorizedError('Missing or wrong API key')
  }
  if (!res.ok) {
    // The API answers errors as problem details; their "detail" is written for people.
    const problem = (await res.json().catch(() => null)) as { detail?: string } | null
    throw new Error(problem?.detail ?? `${init?.method ?? 'GET'} ${url} failed with HTTP ${res.status}`)
  }
  return (res.status === 204 ? undefined : await res.json()) as T
}

export function fetchJobs(statuses: readonly JobStatus[]): Promise<GroupedJobs> {
  return call(`/api/jobs?status=${statuses.join(',')}`)
}

export function updateStatus(id: number, status: JobStatus, apiKey: string): Promise<Job> {
  return call(`/api/jobs/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', 'X-Api-Key': apiKey },
    body: JSON.stringify({ status }),
  })
}

export function ingestNow(apiKey: string): Promise<IngestReport> {
  return call('/api/ingest', { method: 'POST', headers: { 'X-Api-Key': apiKey } })
}

/** A company whose job board is read on every run. */
export interface Company {
  id: number
  provider: string
  slug: string
  name: string
}

export interface AddedCompany {
  company: Company
  openJobs: number
}

export function fetchCompanies(): Promise<Company[]> {
  return call('/api/companies')
}

export function addCompany(url: string, apiKey: string): Promise<AddedCompany> {
  return call('/api/companies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Api-Key': apiKey },
    body: JSON.stringify({ url }),
  })
}

export function removeCompany(id: number, apiKey: string): Promise<void> {
  return call(`/api/companies/${id}`, { method: 'DELETE', headers: { 'X-Api-Key': apiKey } })
}
