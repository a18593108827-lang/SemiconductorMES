import { NavLink, Outlet } from 'react-router-dom'
import { ClipboardList, Factory, Gauge, Package, PauseCircle } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'

const nav = [
  { to: '/track', label: '过账', icon: Gauge, end: true },
  { to: '/track/lots', label: '批次', icon: Package },
  { to: '/track/eqp', label: '设备', icon: Factory },
  { to: '/track/hold', label: '锁批', icon: PauseCircle },
]

export function FieldShell() {
  const { user } = useAuth()

  return (
    <div className="theme-field flex h-full flex-col bg-field-bg text-field-ink">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-field-border bg-field-surface px-4">
        <div className="flex items-center gap-2">
          <ClipboardList className="size-5 text-primary" />
          <span className="font-semibold">现场执行台</span>
        </div>
        <div className="flex gap-6 font-mono text-sm text-field-muted">
          <span>工位 ST-ETCH-01</span>
          <span>设备 EQP-ETCH-A1</span>
          <span>操作员 {user?.userCode ?? '—'}</span>
        </div>
        <NavLink to="/app/dashboard" className="text-sm text-accent hover:underline">
          管理端
        </NavLink>
      </header>
      <div className="flex min-h-0 flex-1">
        <nav className="flex w-[88px] shrink-0 flex-col gap-1 border-r border-field-border bg-field-surface p-2">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex flex-col items-center gap-1 rounded-md px-1 py-3 text-xs transition-colors',
                  isActive
                    ? 'bg-accent/20 text-accent border-l-2 border-accent'
                    : 'border-l-2 border-transparent text-field-muted hover:bg-field-border/40 hover:text-field-ink',
                )
              }
            >
              <item.icon className="size-5" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <main className="min-h-0 flex-1 overflow-auto p-4 text-base">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
