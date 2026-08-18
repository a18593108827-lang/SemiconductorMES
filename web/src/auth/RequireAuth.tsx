import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function needChangePwd(v: number | undefined) {
  return v === 1
}

/** mustChangePwd=1 时仅放行强制改密页 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-muted">加载中…</div>
    )
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  const forced = needChangePwd(user.mustChangePwd)
  const onForcePage = location.pathname === '/app/account/password'

  if (forced && !onForcePage) {
    return <Navigate to="/app/account/password" replace />
  }

  if (!forced && onForcePage) {
    return <Navigate to="/app/account" replace />
  }

  return children
}
