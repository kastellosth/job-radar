import { useEffect, useState, type FormEvent } from 'react'
import { addCompany, errorText, fetchCompanies, removeCompany, type Company } from '../api'
import { formatDate, timeAgo } from '../jobView'

/** How the last reads of the company's board went, in a few words. */
function Health({ company }: { company: Company }) {
  if (company.failingSince) {
    return (
      <span className="health failing" title={company.error ?? undefined}>
        failing since {formatDate(company.failingSince)}
        {company.error && `: ${company.error}`}
        {company.lastReadAt && ` · its ${company.jobs ?? 0} jobs stay listed`}
      </span>
    )
  }
  if (!company.lastReadAt) {
    return <span className="health">not read yet</span>
  }
  const jobs = company.jobs === 1 ? '1 job' : `${company.jobs ?? 0} jobs`
  return (
    <span className="health">
      read {timeAgo(company.lastReadAt)} · {jobs}
    </span>
  )
}

interface Props {
  apiKey: string
}

/** The companies whose job boards are read, with a field to add one by pasting its careers page link. */
export function CompaniesPanel({ apiKey }: Props) {
  const [companies, setCompanies] = useState<Company[]>([])
  const [link, setLink] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let current = true
    fetchCompanies()
      .then((list) => current && setCompanies(list))
      .catch((e: unknown) => current && setNote(errorText(e)))
    return () => {
      current = false
    }
  }, [reloads])

  async function add(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setNote('Checking the job board…')
    try {
      const added = await addCompany(link.trim(), apiKey)
      setNote(`Added ${added.company.name}: ${added.openJobs} open jobs now. They appear after the next fetch.`)
      setLink('')
      setReloads((n) => n + 1)
    } catch (err) {
      setNote(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  async function remove(company: Company) {
    if (!window.confirm(`Stop following ${company.name}? Its board will not be read any more.`)) return
    try {
      await removeCompany(company.id, apiKey)
      setNote(`Removed ${company.name}. Its jobs leave the list in a few days, except ones you applied to.`)
      setReloads((n) => n + 1)
    } catch (err) {
      setNote(errorText(err))
    }
  }

  const failing = companies.filter((c) => c.failingSince).length

  return (
    <details className="companies">
      <summary>
        Companies ({companies.length})
        {failing > 0 && <span className="failing-count"> · {failing} failing</span>}
      </summary>
      <form onSubmit={add}>
        <input
          type="url"
          value={link}
          onChange={(e) => setLink(e.target.value)}
          placeholder="https://apply.workable.com/company"
          aria-label="Careers page link"
          required
        />
        <button type="submit" disabled={busy}>
          Add
        </button>
      </form>
      <p className="hint">
        Workable, Greenhouse, Lever or SmartRecruiters careers page. For Workday (e.g. Accenture, PwC) and
        SuccessFactors sites (e.g. EY, Vodafone, Kotsovolos: links with /search/), search for Greece on the
        company&apos;s site first and paste the results link.
      </p>
      {note && (
        <p className="note" role="note">
          {note}
        </p>
      )}
      <ul>
        {companies.map((c) => (
          <li key={c.id}>
            <span>
              {c.name} <span className="muted">· {c.provider}</span>
              <br />
              <Health company={c} />
            </span>
            <button type="button" onClick={() => remove(c)} aria-label={`Remove ${c.name}`}>
              remove
            </button>
          </li>
        ))}
      </ul>
    </details>
  )
}
