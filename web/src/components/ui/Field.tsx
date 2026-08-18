import { cn } from '../../lib/cn'

interface FieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  fieldSize?: 'md' | 'field'
}

export function Field({ label, error, id, className, fieldSize = 'md', ...rest }: FieldProps) {
  const inputId = id ?? rest.name ?? label
  return (
    <label className="flex flex-col gap-1.5 text-sm" htmlFor={inputId}>
      <span className="text-xs font-medium text-muted">{label}</span>
      <input
        id={inputId}
        className={cn(
          'rounded-md border bg-bg px-3 text-ink placeholder:text-muted/70 disabled:cursor-not-allowed disabled:opacity-60',
          fieldSize === 'md' ? 'h-9' : 'h-11 text-base',
          error ? 'border-danger' : 'border-border focus:border-accent',
          className,
        )}
        {...rest}
      />
      {error ? <span className="text-xs text-danger">{error}</span> : null}
    </label>
  )
}
