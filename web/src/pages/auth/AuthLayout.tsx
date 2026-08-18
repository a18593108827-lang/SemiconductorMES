import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { cn } from '../../lib/cn'

const tabs = [
  { to: '/app/auth/users', label: '用户', code: 'system:user' },
  { to: '/app/auth/roles', label: '角色', code: 'system:role' },
  { to: '/app/auth/perms', label: '权限树', code: 'system:permission' },
  { to: '/app/auth/requests', label: '我的申请', code: 'system:perm-apply' },
  { to: '/app/auth/approvals', label: '审批', code: 'system:perm-approve' },
]

export function AuthLayout() {
  const { hasPermission } = useAuth()
  const visibleTabs = tabs.filter((t) => hasPermission(t.code))

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">系统管理</h1>
        <p className="mt-1 text-sm text-muted">账号、角色与权限配置</p>
      </div>
      <nav className="flex flex-wrap gap-1 border-b border-border" aria-label="系统管理">
        {visibleTabs.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) =>
              cn(
                'relative -mb-px cursor-pointer px-3 py-2 text-sm font-medium transition-colors duration-150',
                isActive
                  ? 'border-b-2 border-primary text-primary'
                  : 'border-b-2 border-transparent text-muted hover:text-ink',
              )
            }
          >
            {t.label}
          </NavLink>
        ))}
      </nav>
      {visibleTabs.length === 0 ? (
        <p className="text-sm text-muted">当前账号无系统管理权限</p>
      ) : (
        <Outlet />
      )}
    </div>
  )
}
