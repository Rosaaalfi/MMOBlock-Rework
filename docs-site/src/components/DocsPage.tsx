import type { ReactNode } from 'react'
import CodeBlock from './CodeBlock'
import InfoBox from './InfoBox'

export interface FieldRow {
  field: string
  value: string
  note: string
}

export interface DocSection {
  id: string
  title: string
  body?: ReactNode
  bullets?: string[]
  fields?: FieldRow[]
  code?: {
    filename: string
    value: string
  }
  note?: {
    type?: 'info' | 'warn' | 'tip'
    value: ReactNode
  }
}

interface Props {
  eyebrow: string
  title: string
  lead: string
  tags?: string[]
  sections: DocSection[]
}

export default function DocsPage({ eyebrow, title, lead, tags = [], sections }: Props) {
  return (
    <article>
      <header className="doc-header">
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p className="lead">{lead}</p>
        {tags.length > 0 && (
          <div className="doc-meta">
            {tags.map((tag) => (
              <span className="pill" key={tag}>{tag}</span>
            ))}
          </div>
        )}
        <nav className="toc" aria-label="Page contents">
          {sections.map((section) => (
            <a key={section.id} href={`#${section.id}`}>{section.title}</a>
          ))}
        </nav>
      </header>

      {sections.map((section) => (
        <section className="doc-section" id={section.id} key={section.id}>
          <h2>{section.title}</h2>
          {section.body}
          {section.bullets && (
            <ul className="check-list">
              {section.bullets.map((bullet) => (
                <li key={bullet}>{bullet}</li>
              ))}
            </ul>
          )}
          {section.fields && <FieldTable rows={section.fields} />}
          {section.code && <CodeBlock filename={section.code.filename}>{section.code.value}</CodeBlock>}
          {section.note && <InfoBox type={section.note.type}>{section.note.value}</InfoBox>}
        </section>
      ))}
    </article>
  )
}

function FieldTable({ rows }: { rows: FieldRow[] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Field</th>
            <th>Value</th>
            <th>Notes</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.field}>
              <td><code className="inline-code">{row.field}</code></td>
              <td>{row.value}</td>
              <td>{row.note}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
