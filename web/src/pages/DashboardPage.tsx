import { useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { LiveDot } from '../components/ui/StatusPill'
import { alarms, equipment, kpi, outputTrend } from '../data/mock'
import { cn } from '../lib/cn'

const eqpColor: Record<string, string> = {
  Idle: 'bg-muted',
  Running: 'bg-success',
  Down: 'bg-danger',
  PM: 'bg-warning',
  Offline: 'bg-border',
}

export function DashboardPage() {
  const [paused, setPaused] = useState(false)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">生产看板</h1>
        <div className="flex items-center gap-3">
          <LiveDot paused={paused} />
          <button
            type="button"
            className="rounded-md border border-border px-2.5 py-1 text-xs text-muted hover:bg-surface"
            onClick={() => setPaused((v) => !v)}
          >
            {paused ? '恢复' : '暂停'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          { label: '在制数', value: kpi.wip, tone: 'text-ink' },
          { label: '锁批数', value: kpi.hold, tone: 'text-warning' },
          { label: '报警数', value: kpi.alarm, tone: 'text-danger' },
          { label: '稼动 %', value: kpi.oee, tone: 'text-success' },
        ].map((item) => (
          <div key={item.label} className="rounded-md border border-border bg-surface px-4 py-3">
            <div className="text-xs font-medium text-muted">{item.label}</div>
            <div className={cn('mt-1 font-mono text-2xl font-semibold tabular-nums', item.tone)}>
              {item.value}
            </div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-3">
        <section className="rounded-md border border-border xl:col-span-2">
          <header className="border-b border-border px-4 py-2.5 text-sm font-medium">
            设备状态矩阵
          </header>
          <div className="grid grid-cols-2 gap-2 p-4 sm:grid-cols-3 md:grid-cols-4">
            {equipment.map((eq) => (
              <div key={eq.id} className="rounded-md border border-border bg-bg p-3">
                <div className="flex items-center gap-2">
                  <span className={cn('size-2.5 rounded-sm', eqpColor[eq.status])} />
                  <span className="font-mono text-xs text-muted">
                    {{ Idle: '空', Running: '运', Down: '故', PM: '保', Offline: '离' }[eq.status]}
                  </span>
                  <span className="truncate text-sm font-medium">{eq.name}</span>
                </div>
                <div className="mt-2 font-mono text-xs text-muted">{eq.id}</div>
                <div className="mt-1 truncate font-mono text-xs text-ink">
                  {eq.currentLot ?? '—'}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-md border border-border">
          <header className="border-b border-border px-4 py-2.5 text-sm font-medium">
            报警流
          </header>
          <ul className="divide-y divide-border">
            {alarms.map((a) => (
              <li key={a.id} className="px-4 py-3 text-sm">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      'size-1.5 rounded-full',
                      a.level === 'critical' && 'bg-danger',
                      a.level === 'warning' && 'bg-warning',
                      a.level === 'info' && 'bg-accent',
                    )}
                  />
                  <span className="font-mono text-xs text-muted">{a.source}</span>
                </div>
                <p className="mt-1 text-ink">{a.message}</p>
                <p className="mt-0.5 font-mono text-xs text-muted">{a.at}</p>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="rounded-md border border-border">
        <header className="border-b border-border px-4 py-2.5 text-sm font-medium">
          产出趋势
        </header>
        <div className="h-56 p-2">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={outputTrend}>
              <CartesianGrid stroke="oklch(0.9 0 0)" strokeDasharray="3 3" />
              <XAxis dataKey="t" tick={{ fontSize: 12 }} stroke="oklch(0.48 0.01 28)" />
              <YAxis tick={{ fontSize: 12 }} stroke="oklch(0.48 0.01 28)" />
              <Tooltip />
              <Area
                type="monotone"
                dataKey="qty"
                stroke="oklch(0.58 0.12 230)"
                fill="oklch(0.58 0.12 230 / 0.2)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </section>
    </div>
  )
}
