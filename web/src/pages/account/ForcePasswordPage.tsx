import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import gsap from 'gsap'
import { ClipboardList, Eye, EyeOff, TriangleAlert } from 'lucide-react'
import { changePasswordApi } from '../../api/auth'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../components/ui/Toast'
import { ApiError } from '../../lib/http'
import { motionMs } from '../../lib/motion'
import { cn } from '../../lib/cn'

export function ForcePasswordPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const panelRef = useRef<HTMLDivElement>(null)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [show, setShow] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!panelRef.current) return
    const d = motionMs() / 1000
    if (d === 0) return
    gsap.from(panelRef.current, {
      y: 8,
      duration: d,
      ease: 'power2.out',
      clearProps: 'transform',
    })
  }, [])

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault()
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
      await logout()
      navigate('/login', { replace: true })
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '修改失败，请重试')
    } finally {
      setSaving(false)
    }
  }

  async function onLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="relative flex h-full items-center justify-center overflow-hidden bg-[oklch(0.14_0.02_28)] px-4">
      <div
        className="pointer-events-none absolute inset-0 opacity-30"
        style={{
          backgroundImage:
            'linear-gradient(oklch(1 0 0 / 0.04) 1px, transparent 1px), linear-gradient(90deg, oklch(1 0 0 / 0.04) 1px, transparent 1px)',
          backgroundSize: '28px 28px',
        }}
        aria-hidden
      />
      <div
        className="pointer-events-none absolute -top-20 right-1/4 h-72 w-40 rotate-[-16deg] bg-primary/30 blur-3xl"
        aria-hidden
      />

      <div
        ref={panelRef}
        className="relative w-full max-w-[400px] rounded-lg border border-[oklch(1_0_0/0.12)] bg-bg p-6 shadow-[0_8px_28px_oklch(0_0_0/0.35)] sm:p-8"
      >
        <div className="mb-5 flex items-center gap-2.5">
          <div className="flex size-9 items-center justify-center rounded-md bg-primary text-white">
            <ClipboardList className="size-5" />
          </div>
          <div className="min-w-0">
            <h1 className="text-lg font-semibold tracking-tight">请先修改密码</h1>
            <p className="truncate text-xs text-muted">
              <span className="font-mono text-ink">{user?.userCode}</span>
              {user?.userName ? ` · ${user.userName}` : null}
            </p>
          </div>
        </div>

        <div className="mb-5 flex gap-2 rounded-md border border-warning/35 bg-warning/10 px-3 py-2.5 text-xs text-ink">
          <TriangleAlert className="mt-0.5 size-3.5 shrink-0 text-warning" />
          <p>管理员已重置密码，或账号要求首次改密。完成后再进入系统。</p>
        </div>

        <form onSubmit={onSubmit} className="space-y-3">
          <Pwd id="force-old" label="当前密码" value={oldPassword} show={show} onChange={setOldPassword} />
          <Pwd id="force-new" label="新密码" value={newPassword} show={show} onChange={setNewPassword} />
          <Pwd id="force-confirm" label="确认新密码" value={confirm} show={show} onChange={setConfirm} />
          <button
            type="button"
            className="inline-flex items-center gap-1 text-xs text-muted transition-colors duration-150 hover:text-ink"
            onClick={() => setShow((v) => !v)}
          >
            {show ? <EyeOff className="size-3.5" /> : <Eye className="size-3.5" />}
            {show ? '隐藏密码' : '显示密码'}
          </button>
          {error ? (
            <div role="alert" className="flex items-start gap-2 text-xs text-danger">
              <TriangleAlert className="mt-0.5 size-3.5 shrink-0" />
              <span>{error}</span>
            </div>
          ) : null}
          <Button type="submit" className="mt-2 w-full" loading={saving}>
            修改并继续
          </Button>
        </form>
        <button
          type="button"
          className="mt-4 w-full text-center text-xs text-muted transition-colors duration-150 hover:text-ink"
          onClick={() => void onLogout()}
        >
          退出登录
        </button>
      </div>
    </div>
  )
}

function Pwd({
  id,
  label,
  value,
  show,
  onChange,
}: {
  id: string
  label: string
  value: string
  show: boolean
  onChange: (v: string) => void
}) {
  return (
    <label className="flex flex-col gap-1.5 text-sm" htmlFor={id}>
      <span className="text-xs font-medium text-muted">{label}</span>
      <input
        id={id}
        type={show ? 'text' : 'password'}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          'h-9 w-full rounded-md border border-border bg-bg px-3 text-ink transition-colors duration-150 focus:border-accent',
        )}
      />
    </label>
  )
}
