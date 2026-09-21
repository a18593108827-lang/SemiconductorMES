import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { SpcSeries } from '../../api/spc'

function num(v: number | string | null | undefined): number | null {
  if (v == null || v === '') return null
  const n = typeof v === 'number' ? v : Number(v)
  return Number.isFinite(n) ? n : null
}

function fmtTick(iso: string | null | undefined) {
  if (!iso) return ''
  return iso.replace('T', ' ').slice(5, 16)
}

type Row = {
  idx: number
  label: string
  value: number | null
  mr: number | null
  oos: boolean
  ooc: boolean
  lotNo: string
}

function buildRows(series: SpcSeries): Row[] {
  const pts = series.points ?? []
  const rows: Row[] = []
  let prev: number | null = null
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i]
    const value = num(p.value)
    const mr = value != null && prev != null ? Math.abs(value - prev) : null
    if (value != null) prev = value
    rows.push({
      idx: i + 1,
      label: fmtTick(p.time),
      value,
      mr,
      oos: p.itemResult === 'OOS',
      ooc: !!p.evalOoc,
      lotNo: p.lotNo ?? '',
    })
  }
  return rows
}

interface Props {
  series: SpcSeries
  highlightIdx: number | null
  onSelectIdx: (idx: number | null) => void
}

export function SpcTrendCharts({ series, highlightIdx, onSelectIdx }: Props) {
  const rows = buildRows(series)
  const chart = series.chart
  const ucl = num(chart.ucl)
  const cl = num(chart.cl)
  const lcl = num(chart.lcl)
  const usl = num(series.specUsl)
  const lsl = num(series.specLsl)
  const hasMr = rows.some((r) => r.mr != null)

  return (
    <div className="space-y-3">
      <section className="rounded-md border border-border">
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-2.5">
          <span className="text-sm font-medium">I 图（单值）</span>
          <div className="flex flex-wrap gap-3 text-xs text-muted">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-px w-4 bg-accent" />
              控制限
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-px w-4 border-t border-dashed border-warning" />
              规格（不判 OOC）
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-danger" />
              OOC
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full border border-warning bg-bg" />
              OOS
            </span>
          </div>
        </header>
        <div className="h-64 p-2">
          {rows.length === 0 ? (
            <div className="flex h-full items-center justify-center text-sm text-muted">暂无采集点</div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={rows}
                onClick={(state) => {
                  // recharts 3 移除了 activePayload，改用 activeTooltipIndex（数据数组下标）
                  const i = state?.activeTooltipIndex
                  const row = typeof i === 'number' ? rows[i] : undefined
                  onSelectIdx(row ? row.idx : null)
                }}
              >
                <CartesianGrid stroke="oklch(0.9 0 0)" strokeDasharray="3 3" />
                <XAxis dataKey="idx" tick={{ fontSize: 11 }} stroke="oklch(0.48 0.01 28)" tickFormatter={(v) => String(v)} />
                <YAxis tick={{ fontSize: 11 }} stroke="oklch(0.48 0.01 28)" domain={['auto', 'auto']} />
                <Tooltip
                  contentStyle={{ fontSize: 12 }}
                  formatter={(value) => [value, '量测值']}
                  labelFormatter={(_, payload) => {
                    const row = payload?.[0]?.payload as Row | undefined
                    if (!row) return ''
                    return `#${row.idx} ${row.label}${row.lotNo ? ` · ${row.lotNo}` : ''}`
                  }}
                />
                {ucl != null ? (
                  <ReferenceLine y={ucl} stroke="oklch(0.58 0.12 230)" strokeWidth={1.5} label={{ value: 'UCL', fill: 'oklch(0.48 0.01 28)', fontSize: 10 }} />
                ) : null}
                {cl != null ? (
                  <ReferenceLine y={cl} stroke="oklch(0.45 0.02 250)" strokeWidth={1.5} strokeDasharray="4 2" label={{ value: 'CL', fill: 'oklch(0.48 0.01 28)', fontSize: 10 }} />
                ) : null}
                {lcl != null ? (
                  <ReferenceLine y={lcl} stroke="oklch(0.58 0.12 230)" strokeWidth={1.5} label={{ value: 'LCL', fill: 'oklch(0.48 0.01 28)', fontSize: 10 }} />
                ) : null}
                {usl != null ? <ReferenceLine y={usl} stroke="oklch(0.72 0.14 75)" strokeDasharray="6 4" strokeWidth={1} /> : null}
                {lsl != null ? <ReferenceLine y={lsl} stroke="oklch(0.72 0.14 75)" strokeDasharray="6 4" strokeWidth={1} /> : null}
                {highlightIdx != null ? (
                  <ReferenceLine x={highlightIdx} stroke="oklch(0.55 0.12 230 / 0.4)" strokeWidth={2} />
                ) : null}
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="oklch(0.4 0.02 250)"
                  strokeWidth={1.5}
                  connectNulls={false}
                  isAnimationActive={false}
                  dot={(props) => {
                    const { cx, cy, payload } = props
                    if (cx == null || cy == null || payload?.value == null) return null
                    const active = highlightIdx === payload.idx
                    if (payload.ooc) {
                      return <circle cx={cx} cy={cy} r={active ? 6 : 5} fill="oklch(0.55 0.2 25)" />
                    }
                    if (payload.oos) {
                      return (
                        <circle
                          cx={cx}
                          cy={cy}
                          r={active ? 5 : 4}
                          fill="oklch(0.98 0.01 90)"
                          stroke="oklch(0.72 0.14 75)"
                          strokeWidth={2}
                        />
                      )
                    }
                    return <circle cx={cx} cy={cy} r={active ? 4.5 : 3} fill="oklch(0.4 0.02 250)" />
                  }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>

      <section className="rounded-md border border-border">
        <header className="border-b border-border px-4 py-2.5 text-sm font-medium">MR 图（移动极差）</header>
        <div className="h-40 p-2">
          {!hasMr ? (
            <div className="flex h-full items-center justify-center text-sm text-muted">点不够，算不出移动极差</div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={rows}>
                <CartesianGrid stroke="oklch(0.9 0 0)" strokeDasharray="3 3" />
                <XAxis dataKey="idx" tick={{ fontSize: 11 }} stroke="oklch(0.48 0.01 28)" />
                <YAxis tick={{ fontSize: 11 }} stroke="oklch(0.48 0.01 28)" domain={[0, 'auto']} />
                <Tooltip contentStyle={{ fontSize: 12 }} formatter={(v) => [v, 'MR']} />
                <Line
                  type="monotone"
                  dataKey="mr"
                  stroke="oklch(0.55 0.1 160)"
                  strokeWidth={1.5}
                  dot={{ r: 2.5 }}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </section>
    </div>
  )
}
