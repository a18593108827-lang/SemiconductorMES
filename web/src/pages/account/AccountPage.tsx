import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import { CheckCircle2, Eye, EyeOff, KeyRound, TriangleAlert } from 'lucide-react'
import { changePasswordApi } from '../../api/auth'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../components/ui/Toast'
import { ApiError } from '../../lib/http'
import { motionMs } from '../../lib/motion'
import { cn } from '../../lib/cn'

function pwdStrength(pwd: string): { score: 0 | 1 | 2 | 3; label: string } {
  if (!pwd) return { score: 0, label: '' }
  let s = 0
  if (pwd.length >= 6) s++
  if (pwd.length >= 10) s++
  if (/[A-Za-z]/.test(pwd) && /\d/.test(pwd)) s++
  if (/[^A-Za-z0-9]/.test(pwd)) s = Math.min(3, s + 1) as 0 | 1 | 2 | 3
  const labels = ['', '较弱', '一般', '较好'] as const
  return { score: s as 0 | 1 | 2 | 3, label: labels[s] }
}

export function AccountPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const rootRef = useRef<HTMLDivElement>(null)
  const okRef = useRef<HTMLDivElement>(null)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showOld, setShowOld] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [saving, setSaving] = useState(false)
  const strength = pwdStrength(newPassword)
  const initials = (user?.userName || user?.userCode || '?').slice(0, 1).toUpperCase()

  useEffect(() => {
    if (!rootRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    const ctx = gsap.context(() => {
      gsap.from('.account-block', {
        y: 6,
        duration: d,
        stagger: 0.05,
        ease: 'power2.out',
        clearProps: 'transform',
      })
    }, rootRef)
    return () => ctx.revert()
  }, [])

  useEffect(() => {
    if (!ok || !okRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.fromTo(
      okRef.current,
      { autoAlpha: 0, y: 4 },
      { autoAlpha: 1, y: 0, duration: d, ease: 'power2.out' },
    )
  }, [ok])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setOk('')
    if (!oldPassword || !newPassword) {
      setError('请填写当前密码和新密码')
      return
    }
    if (newPassword.length < 6) {
      setError('新密码至少 6 位')
      return
    }
    if (newPassword === oldPassword) {
      setError('新密码不能与旧密码相同')
      return
    }
    if (newPassword !== confirm) {
      setError('两次输入的新密码不一致')
      return
    }
    setSaving(true)
    setError('')
    try {
      await changePasswordApi({ oldPassword, newPassword })
      setOk('密码已更新，请重新登录')
      setOldPassword('')
      setNewPassword('')
      setConfirm('')
      await logout()
      navigate('/login', { replace: true })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '修改失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div ref={rootRef} className="mx-auto max-w-3xl space-y-8">
      <header className="account-block flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">账号设置</h1>
          <p className="mt-1 text-sm text-muted">本人资料只读；安全设置在此修改登录密码</p>
        </div>
      </header>

      <section className="account-block">
        <div className="flex flex-wrap items-center gap-4 border-b border-border pb-5">
          <div
            className="flex size-14 shrink-0 items-center justify-center rounded-md bg-primary font-semibold text-white"
            aria-hidden
          >
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate font-mono text-lg font-semibold tracking-tight">
              {user?.userCode ?? '—'}
            </div>
            <div className="mt-0.5 truncate text-sm text-muted">{user?.userName || '未设置姓名'}</div>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {(user?.roles?.length ? user.roles : []).map((r) => (
              <span
                key={r}
                className="rounded-sm border border-border bg-surface px-2 py-1 font-mono text-xs text-ink"
              >
                {r}
              </span>
            ))}
            {!user?.roles?.length ? (
              <span className="text-xs text-muted">暂无角色</span>
            ) : null}
          </div>
        </div>
        <dl className="mt-4 grid gap-x-8 gap-y-3 sm:grid-cols-2">
          <div className="flex items-baseline justify-between gap-3 border-b border-border/80 py-2 sm:block sm:border-0 sm:py-0">
            <dt className="text-xs font-medium text-muted">用户编码</dt>
            <dd className="font-mono text-sm font-medium sm:mt-1">{user?.userCode ?? '—'}</dd>
          </div>
          <div className="flex items-baseline justify-between gap-3 border-b border-border/80 py-2 sm:block sm:border-0 sm:py-0">
            <dt className="text-xs font-medium text-muted">显示姓名</dt>
            <dd className="text-sm font-medium sm:mt-1">{user?.userName || '—'}</dd>
          </div>
        </dl>
      </section>

      <section className="account-block rounded-md border border-border bg-bg">
        <header className="flex items-start gap-3 border-b border-border px-4 py-3.5 sm:px-5">
          <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-md bg-surface text-muted">
            <KeyRound className="size-4" />
          </span>
          <div>
            <h2 className="text-sm font-semibold">修改密码</h2>
            <p className="mt-0.5 text-xs text-muted">至少 6 位；建议字母与数字组合。成功后需重新登录。</p>
          </div>
        </header>
        <form onSubmit={onSubmit} className="space-y-4 p-4 sm:p-5 sm:max-w-md">
          <PwdField
            id="oldPassword"
            label="当前密码"
            value={oldPassword}
            show={showOld}
            onToggle={() => setShowOld((v) => !v)}
            onChange={setOldPassword}
            autoComplete="current-password"
          />
          <div className="space-y-2">
            <PwdField
              id="newPassword"
              label="新密码"
              value={newPassword}
              show={showNew}
              onToggle={() => setShowNew((v) => !v)}
              onChange={setNewPassword}
              autoComplete="new-password"
            />
            {newPassword ? (
              <div className="space-y-1.5" aria-live="polite">
                <div className="flex gap-1" aria-hidden>
                  {[1, 2, 3].map((i) => (
                    <span
                      key={i}
                      className={cn(
                        'h-1 flex-1 rounded-sm transition-colors duration-150',
                        strength.score >= i
                          ? i === 1
                            ? 'bg-danger'
                            : i === 2
                              ? 'bg-warning'
                              : 'bg-success'
                          : 'bg-border',
                      )}
                    />
                  ))}
                </div>
                <p className="text-xs text-muted">强度：{strength.label || '—'}</p>
              </div>
            ) : null}
          </div>
          <PwdField
            id="confirmPassword"
            label="确认新密码"
            value={confirm}
            show={showNew}
            onToggle={() => setShowNew((v) => !v)}
            onChange={setConfirm}
            autoComplete="new-password"
          />

          {error ? (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-md border border-danger/25 bg-danger/5 px-3 py-2 text-xs text-danger"
            >
              <TriangleAlert className="mt-0.5 size-3.5 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}
          {ok ? (
            <div
              ref={okRef}
              role="status"
              className="flex items-start gap-2 rounded-md border border-success/25 bg-success/5 px-3 py-2 text-xs text-ink"
            >
              <CheckCircle2 className="mt-0.5 size-3.5 shrink-0 text-success" />
              <span>{ok}</span>
            </div>
          ) : null}

          <div className="pt-1">
            <Button type="submit" loading={saving}>
              修改密码
            </Button>
          </div>
        </form>
      </section>
    </div>
  )
}

function PwdField({
  id,
  label,
  value,
  show,
  onToggle,
  onChange,
  autoComplete,
}: {
  id: string
  label: string
  value: string
  show: boolean
  onToggle: () => void
  onChange: (v: string) => void
  autoComplete?: string
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm" htmlFor={id}>
      <span className="text-xs font-medium text-muted">{label}</span>
      <div className="relative">
        <input
          id={id}
          type={show ? 'text' : 'password'}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete}
          className="h-9 w-full rounded-md border border-border bg-bg pr-10 pl-3 text-ink transition-colors duration-150 focus:border-accent"
        />
        <button
          type="button"
          className="absolute top-1/2 right-1.5 -translate-y-1/2 rounded p-1.5 text-muted transition-colors duration-150 hover:text-ink"
          onClick={onToggle}
          aria-label={show ? '隐藏密码' : '显示密码'}
        >
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
    </label>
  )
}
