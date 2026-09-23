import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// 未开启 vitest globals 时 RTL 不会自动挂 afterEach，必须显式清理，
// 否则上个用例的 DOM 残留会让 getByRole 报「Found multiple elements」。
afterEach(() => cleanup())
