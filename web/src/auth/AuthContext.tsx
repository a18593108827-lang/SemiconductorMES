import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { loginApi, logoutApi, userInfoApi, type UserInfo } from '../api/auth'
import { clearToken, getToken, setToken } from '../lib/http'

interface AuthContextValue {
  user: UserInfo | null
  loading: boolean
  login: (userCode: string, password: string) => Promise<UserInfo>
  logout: () => Promise<void>
  refresh: () => Promise<void>
  hasPermission: (code: string) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null)
  const [loading, setLoading] = useState(() => !!getToken())

  const refresh = useCallback(async () => {
    if (!getToken()) {
      setUser(null)
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const info = await userInfoApi()
      setUser({
        ...info,
        roles: info.roles ?? [],
        permissions: info.permissions ?? [],
        menus: info.menus ?? [],
      })
    } catch {
      clearToken()
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const login = useCallback(async (userCode: string, password: string) => {
    const res = await loginApi(userCode, password)
    setToken(res.token)
    const info = await userInfoApi()
    const userInfo: UserInfo = {
      ...info,
      roles: info.roles ?? [],
      permissions: info.permissions ?? [],
      menus: info.menus ?? [],
    }
    setUser(userInfo)
    return userInfo
  }, [])

  const logout = useCallback(async () => {
    try {
      if (getToken()) await logoutApi()
    } catch {
      // ignore
    } finally {
      clearToken()
      setUser(null)
    }
  }, [])

  const hasPermission = useCallback(
    (code: string) => !!user?.permissions?.includes(code),
    [user],
  )

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh, hasPermission }),
    [user, loading, login, logout, refresh, hasPermission],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
