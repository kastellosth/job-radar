import { useEffect, useState, type FormEvent } from 'react'
import { addCompany, errorText, fetchCompanies, removeCompany, type Company } from '../api'

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
    try {
      await removeCompany(company.id, apiKey)
      setNote(`Removed ${company.name}. Jobs already collected stay.`)
      setReloads((n) => n + 1)
    } catch (err) {
      setNote(errorText(err))
    }
  }

  return (
    <details className="companies">
      <summary>Companies ({companies.length})</summary>
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
      <p className="hint">Workable, Greenhouse, Lever or SmartRecruiters careers page.</p>
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
