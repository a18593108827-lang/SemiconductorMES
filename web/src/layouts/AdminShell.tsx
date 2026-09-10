import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  Activity,
  BarChart2,
  Bell,
  Boxes,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Factory,
  FlaskConical,
  History,
  LayoutDashboard,
  Lock,
  LogOut,
  Map,
  Package,
  PauseCircle,
  Ruler,
  Search,
  Settings,
  Shield,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { listCriticalAlarmsApi } from '../api/alarm'
import { useAuth } from '../auth/AuthContext'
import type { MenuItem } from '../api/auth'
import { subscribeAlarmActive } from '../lib/alarmWs'
import { cn } from '../lib/cn'

const iconMap: Record<string, LucideIcon> = {
  factory: Factory,
  'layout-dashboard': LayoutDashboard,
  package: Package,
  boxes: Boxes,
  map: Map,
  'pause-circle': PauseCircle,
  bell: Bell,
  history: History,
  lock: Lock,
  settings: Settings,
  shield: Shield,
  users: Users,
  'flask-conical': FlaskConical,
  'clipboard-list': ClipboardList,
  ruler: Ruler,
  activity: Activity,
  'bar-chart-2': BarChart2,
}

function resolveIcon(name: string | null | undefined): LucideIcon {
  if (!name) return ClipboardList
  return iconMap[name] ?? ClipboardList
}

type NavLeaf = { to: string; label: string; icon: LucideIcon }
type NavGroup = { key: string; title: string; items: NavLeaf[] }

function buildNavGroups(menus: MenuItem[]): NavGroup[] {
  const groups: NavGroup[] = []

  for (const root of menus) {
    if (root.permType === 1) {
      const isSystem = root.permCode === 'system' || root.permName === '系统管理'
      if (isSystem) {
        const children = root.children ?? []
        if (children.some((c) => !!c.path)) {
          groups.push({
            key: String(root.id),
            title: '',
            items: [
              {
                to: '/app/auth',
                label: root.permName,
                icon: resolveIcon(root.icon ?? 'settings'),
              },
            ],
          })
        }
        continue
      }

      const items: NavLeaf[] = (root.children ?? [])
        .filter((c) => !!c.path)
        .map((c) => ({
          to: c.path as string,
          label: c.permName,
          icon: resolveIcon(c.icon),
        }))
      if (items.length > 0) {
        groups.push({
          key: String(root.id),
          title: root.permName,
          items,
        })
      }
      continue
    }

    if (root.path) {
      groups.push({
        key: String(root.id),
        title: '',
        items: [
          {
            to: root.path,
            label: root.permName,
            icon: resolveIcon(root.icon),
          },
        ],
      })
    }
  }

  return groups
}

export function AdminShell() {
  const [collapsed, setCollapsed] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuth()
  const [criticalCount, setCriticalCount] = useState(0)
  const canAlarm = !!user?.permissions?.includes('alarm:view')
  const groups = useMemo(() => buildNavGroups(user?.menus ?? []), [user?.menus])
  const isDashboard = location.pathname === '/app/dashboard'

  useEffect(() => {
    if (!menuOpen) return
    function onDoc(e: MouseEvent) {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [menuOpen])

  useEffect(() => {
    if (!canAlarm) {
      setCriticalCount(0)
      return
    }
    let alive = true
    const refresh = async () => {
      try {
        const list = await listCriticalAlarmsApi()
        if (alive) setCriticalCount(list.length)
      } catch {
        if (alive) setCriticalCount(0)
      }
    }
    void refresh()
    const unsub = subscribeAlarmActive(() => {
      void refresh()
    })
    return () => {
      alive = false
      unsub()
    }
  }, [canAlarm])

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-full bg-bg text-ink">
      <aside
        className={cn(
          'flex shrink-0 flex-col border-r border-border bg-surface transition-[width] duration-200 ease-out',
          collapsed ? 'w-14' : 'w-60',
        )}
      >
        <div className="flex h-14 items-center gap-2 border-b border-border px-3">
          <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-primary text-white">
            <ClipboardList className="size-4" />
          </div>
          {!collapsed ? (
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold tracking-tight">MES</div>
              <div className="truncate text-[11px] text-muted">制造执行</div>
            </div>
          ) : null}
        </div>
        <nav className="flex-1 space-y-4 overflow-y-auto p-2">
          {groups.map((group) => (
            <div key={group.key} className="space-y-0.5">
              {group.title && !collapsed ? (
                <div className="px-2.5 pb-1 text-xs font-medium text-muted">{group.title}</div>
              ) : null}
              {collapsed && group.title ? (
                <div className="mx-auto my-1.5 h-px w-6 bg-border" aria-hidden />
              ) : null}
              {group.items.map((item) => (
                <NavLink
                  key={`${group.key}-${item.to}`}
                  to={item.to}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 rounded-md px-2.5 py-2 text-sm transition-colors duration-150',
                      isActive
                        ? 'bg-primary/10 font-medium text-primary'
                        : 'text-ink hover:bg-border/50',
                      collapsed && 'justify-center px-0',
                    )
                  }
                  title={item.label}
                >
                  <item.icon className="size-4 shrink-0" />
                  {!collapsed ? item.label : null}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="border-t border-border p-2">
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm text-muted transition-colors duration-150 hover:bg-border/50 hover:text-ink"
            onClick={() => setCollapsed((v) => !v)}
            aria-label={collapsed ? '展开侧栏' : '收起侧栏'}
          >
            {collapsed ? <ChevronRight className="size-4" /> : <ChevronLeft className="size-4" />}
            {!collapsed ? '收起' : null}
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 shrink-0 items-center gap-3 border-b border-border bg-bg px-4">
          <div className="relative max-w-md flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted" />
            <input
              className="h-9 w-full rounded-md border border-border bg-surface pr-3 pl-9 text-sm transition-colors duration-150 placeholder:text-muted/80 focus:border-accent focus:bg-bg"
              placeholder="搜索批次 / 设备 / 配方"
              aria-label="全局搜索"
            />
            <kbd className="pointer-events-none absolute top-1/2 right-2 hidden -translate-y-1/2 rounded border border-border bg-bg px-1.5 py-0.5 font-mono text-[10px] text-muted sm:inline">
              ⌘K
            </kbd>
          </div>
          {canAlarm ? (
            <button
              type="button"
              className="relative rounded-md p-2 text-muted transition-colors duration-150 hover:bg-surface hover:text-ink"
              aria-label={criticalCount > 0 ? `报警，${criticalCount} 条严重未关闭` : '报警'}
              onClick={() => navigate('/app/alarm')}
            >
              <Bell className="size-5" />
              {criticalCount > 0 ? (
                <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-danger ring-2 ring-bg" />
              ) : null}
            </button>
          ) : null}
          <div className="hidden h-6 w-px bg-border sm:block" aria-hidden />
          <div className="relative" ref={menuRef}>
            <button
              type="button"
              className="flex min-w-0 items-center gap-2 rounded-md px-1.5 py-1 text-sm transition-colors duration-150 hover:bg-surface"
              onClick={() => setMenuOpen((v) => !v)}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
            >
              <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-surface text-muted">
                <Users className="size-3.5" />
              </span>
              <div className="hidden min-w-0 leading-tight text-left sm:block">
                <div className="truncate font-mono text-[13px] font-medium">{user?.userCode ?? '—'}</div>
                {user?.userName ? (
                  <div className="truncate text-[11px] text-muted">{user.userName}</div>
                ) : null}
              </div>
              <ChevronDown className={cn('size-3.5 shrink-0 text-muted transition-transform', menuOpen && 'rotate-180')} />
            </button>
            {menuOpen ? (
              <div
                role="menu"
                className="absolute top-full right-0 z-50 mt-1 w-44 overflow-hidden rounded-md border border-border bg-bg py-1 shadow-[0_8px_24px_oklch(0_0_0/0.12)]"
              >
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-surface"
                  onClick={() => {
                    setMenuOpen(false)
                    navigate('/app/account')
                  }}
                >
                  <UserRound className="size-4 text-muted" />
                  账号设置
                </button>
                <div className="my-1 h-px bg-border" />
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-danger hover:bg-surface"
                  onClick={() => {
                    setMenuOpen(false)
                    void onLogout()
                  }}
                >
                  <LogOut className="size-4" />
                  退出登录
                </button>
              </div>
            ) : null}
          </div>
        </header>
        <main
          className={cn(
            'min-h-0 flex-1 bg-bg',
            isDashboard
              ? 'flex flex-col overflow-hidden p-3'
              : 'overflow-auto p-4 md:p-5',
          )}
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}
