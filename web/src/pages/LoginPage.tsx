import { useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import { ClipboardList, Eye, EyeOff } from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'
import { kpi } from '../data/mock'
import { ApiError } from '../lib/http'
import { motionMs } from '../lib/motion'
import { cn } from '../lib/cn'

const statusRows = [
  { label: '在制 WIP', value: String(kpi.wip), tone: 'bg-accent' },
  { label: '锁批 Hold', value: String(kpi.hold), tone: 'bg-warning' },
  { label: '报警 Alarm', value: String(kpi.alarm), tone: 'bg-danger' },
]

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, loading, login } = useAuth()
  const [userCode, setUserCode] = useState('admin')
  const [password, setPassword] = useState('123456')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  const from = (location.state as { from?: string } | null)?.from || '/app/dashboard'

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    const ctx = gsap.context(() => {
      if (d === 0) {
        gsap.set(['.login-brand', '.login-stat', '.login-panel'], { clearProps: 'all', autoAlpha: 1, y: 0 })
        return
      }
      gsap.set('.login-brand', { autoAlpha: 0, y: 12 })
      gsap.set('.login-stat', { autoAlpha: 0, y: 8 })
      gsap.set('.login-panel', { autoAlpha: 0, y: 10 })
      const tl = gsap.timeline({ defaults: { ease: 'power2.out' } })
      tl.to('.login-brand', { autoAlpha: 1, y: 0, duration: d })
        .to('.login-stat', { autoAlpha: 1, y: 0, duration: d, stagger: 0.05 }, '-=0.08')
        .to('.login-panel', { autoAlpha: 1, y: 0, duration: d }, '-=0.12')
    }, rootRef)
    return () => ctx.revert()
  }, [])

  if (!loading && user) {
    const dest = user.mustChangePwd === 1 ? '/app/account/password' : from
    return <Navigate to={dest} replace />
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!userCode || !password) {
      setError('请输入用户编码和密码')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const info = await login(userCode.trim(), password)
      navigate(info.mustChangePwd === 1 ? '/app/account/password' : from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div ref={rootRef} className="flex h-full bg-bg text-ink">
      <aside className="relative hidden w-[44%] shrink-0 overflow-hidden bg-[oklch(0.14_0.02_28)] text-[oklch(0.93_0_0)] md:flex md:flex-col md:justify-between md:p-10 lg:p-12">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.35]"
          style={{
            backgroundImage:
              'linear-gradient(oklch(1 0 0 / 0.045) 1px, transparent 1px), linear-gradient(90deg, oklch(1 0 0 / 0.045) 1px, transparent 1px)',
            backgroundSize: '32px 32px',
          }}
          aria-hidden
        />
        <div
          className="pointer-events-none absolute -top-24 -right-16 h-[420px] w-[220px] rotate-[-18deg] bg-primary/25 blur-3xl"
          aria-hidden
        />
        <div
          className="pointer-events-none absolute bottom-0 left-0 h-1 w-full bg-gradient-to-r from-primary via-primary/40 to-transparent"
          aria-hidden
        />

        <div className="login-brand relative">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2.5">
              <div className="flex size-9 items-center justify-center rounded-md bg-primary text-white">
                <ClipboardList className="size-5" />
              </div>
              <span className="text-lg font-semibold tracking-tight">MES</span>
            </div>
            <span className="rounded border border-[oklch(1_0_0/0.16)] bg-[oklch(1_0_0/0.06)] px-2 py-0.5 font-mono text-[11px] tracking-wide text-[oklch(0.78_0_0)]">
              DEMO
            </span>
          </div>
          <h1 className="mt-12 max-w-[14ch] text-3xl font-semibold leading-[1.2] tracking-tight text-balance lg:text-[2rem]">
            制造执行，状态一眼可判
          </h1>
          <p className="mt-4 max-w-[34ch] text-sm leading-relaxed text-[oklch(0.72_0_0)]">
            Lot 全生命周期执行与追溯。现场 Track，管理端看板与权限，同一套状态语义。
          </p>
        </div>

        <div className="relative space-y-2">
          {statusRows.map((row) => (
            <div
              key={row.label}
              className="login-stat flex items-center justify-between rounded-md border border-[oklch(1_0_0/0.1)] bg-[oklch(1_0_0/0.04)] px-3.5 py-2.5"
            >
              <div className="flex items-center gap-2.5">
                <span className={cn('size-2 rounded-sm', row.tone)} />
                <span className="text-sm text-[oklch(0.78_0_0)]">{row.label}</span>
              </div>
              <span className="font-mono text-sm font-medium tabular-nums">{row.value}</span>
            </div>
          ))}
          <p className="pt-3 font-mono text-[11px] text-[oklch(0.5_0_0)]">Tempered Steel Cleanroom</p>
        </div>
      </aside>

      <div className="flex flex-1 items-center justify-center bg-surface px-4 py-8">
        <form
          onSubmit={submit}
          className="login-panel w-full max-w-[400px] rounded-lg border border-border bg-bg p-6 shadow-[0_8px_24px_oklch(0_0_0/0.06)] sm:p-8"
        >
          <div className="mb-6">
            <div className="mb-4 flex items-center gap-2 md:hidden">
              <div className="flex size-8 items-center justify-center rounded-md bg-primary text-white">
                <ClipboardList className="size-4" />
              </div>
              <span className="font-semibold">MES</span>
              <span className="ml-auto rounded border border-border px-1.5 py-0.5 font-mono text-[10px] text-muted">
                DEMO
              </span>
            </div>
            <h2 className="text-xl font-semibold tracking-tight">进入系统</h2>
            <p className="mt-1 text-sm text-muted">使用工号编码登录管理端或现场台</p>
          </div>
          <div className="space-y-3">
            <Field
              label="用户编码"
              name="userCode"
              value={userCode}
              onChange={(e) => setUserCode(e.target.value)}
              autoComplete="username"
            />
            <label className="flex flex-col gap-1.5 text-sm" htmlFor="password">
              <span className="text-xs font-medium text-muted">密码</span>
              <div className="relative">
                <input
                  id="password"
                  name="password"
                  type={showPwd ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  className={cn(
                    'h-9 w-full rounded-md border bg-bg pr-10 pl-3 text-ink',
                    error ? 'border-danger' : 'border-border focus:border-accent',
                  )}
                />
                <button
                  type="button"
                  className="absolute top-1/2 right-1.5 -translate-y-1/2 rounded p-1.5 text-muted hover:text-ink"
                  onClick={() => setShowPwd((v) => !v)}
                  aria-label={showPwd ? '隐藏密码' : '显示密码'}
                >
                  {showPwd ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              {error ? <span className="text-xs text-danger">{error}</span> : null}
            </label>
          </div>
          <Button type="submit" className="mt-6 w-full" loading={submitting}>
            登录
          </Button>
          <p className="mt-4 text-xs text-muted">
            现场台登录后从侧栏进入。演示账号{' '}
            <span className="font-mono text-ink">admin / 123456</span>
          </p>
        </form>
      </div>
    </div>
  )
}
