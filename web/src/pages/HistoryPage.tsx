import { history } from '../data/mock'

export function HistoryPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">履历追溯</h1>
      <ol className="relative space-y-0 border-l border-border ml-3">
        {history.map((e) => (
          <li key={e.id} className="relative pb-6 pl-6">
            <span className="absolute top-1.5 -left-[5px] size-2.5 rounded-full border-2 border-bg bg-accent" />
            <div className="flex flex-wrap items-baseline gap-2">
              <span className="text-sm font-medium">{e.type}</span>
              <span className="font-mono text-xs text-accent">{e.lotId}</span>
              <span className="font-mono text-xs text-muted">{e.at}</span>
            </div>
            <p className="mt-1 text-sm text-ink">{e.detail}</p>
            <p className="mt-0.5 font-mono text-xs text-muted">操作人 {e.by}</p>
          </li>
        ))}
      </ol>
    </div>
  )
}
