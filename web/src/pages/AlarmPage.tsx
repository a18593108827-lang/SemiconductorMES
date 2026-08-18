import { AlertTriangle } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { alarms } from '../data/mock'
import { cn } from '../lib/cn'

const levelLabel = {
  critical: '严重',
  warning: '警告',
  info: '提示',
} as const

export function AlarmPage() {
  const critical = alarms.find((a) => a.level === 'critical' && !a.ack)

  return (
    <div className="space-y-4">
      {critical ? (
        <div className="flex items-center gap-3 rounded-md border border-danger bg-danger/10 px-4 py-3 text-sm">
          <AlertTriangle className="size-5 text-danger" />
          <div className="min-w-0 flex-1">
            <div className="font-medium text-danger">严重 · {critical.source}</div>
            <div className="truncate text-ink">{critical.message}</div>
          </div>
          <Button variant="danger" className="h-8 shrink-0">
            确认
          </Button>
        </div>
      ) : null}

      <h1 className="text-xl font-semibold">报警管理</h1>
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-surface text-xs font-medium text-muted">
            <tr className="h-10 border-b border-border">
              <th className="px-3">级别</th>
              <th className="px-3">来源</th>
              <th className="px-3">消息</th>
              <th className="px-3">时间</th>
              <th className="px-3">确认</th>
            </tr>
          </thead>
          <tbody>
            {alarms.map((a) => (
              <tr key={a.id} className="h-10 border-b border-border hover:bg-surface/80">
                <td className="px-3">
                  <span className="inline-flex items-center gap-1.5">
                    <span
                      className={cn(
                        'size-1.5 rounded-full',
                        a.level === 'critical' && 'bg-danger',
                        a.level === 'warning' && 'bg-warning',
                        a.level === 'info' && 'bg-accent',
                      )}
                    />
                    {levelLabel[a.level]}
                  </span>
                </td>
                <td className="px-3 font-mono text-[13px]">{a.source}</td>
                <td className="px-3">{a.message}</td>
                <td className="px-3 font-mono text-xs text-muted">{a.at}</td>
                <td className="px-3">{a.ack ? '已确认' : '未确认'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
