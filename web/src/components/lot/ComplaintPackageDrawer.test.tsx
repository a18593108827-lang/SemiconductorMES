import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { UserEvent } from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * EVAL-0001 回归用例：追溯包抽屉的下载重入闸与会话归属。
 *
 * 背景：抽屉 footer 有「下载 JSON」「下载 ZIP」两个按钮，若无重入保护，连点两下会并发下载，
 * 且先完成者的 `finally` 会清掉后者的 loading；抽屉重开后旧请求还会清新会话状态。
 * 这两条用例正是那两个故障模式的自动防线（详见 docs/eval/EVAL-0001-*.md）。
 *
 * 全部依赖 mock，不触网、不依赖后端（K1）；不改生产代码（K2）。
 */
const mocks = vi.hoisted(() => ({
  preview: vi.fn(),
  build: vi.fn(),
  export: vi.fn(),
  contain: vi.fn(),
  reasons: vi.fn(),
  toastError: vi.fn(),
}))

vi.mock('../../api/complaint', () => ({
  previewComplaintPackageApi: mocks.preview,
  buildComplaintPackageApi: mocks.build,
  exportComplaintPackageApi: mocks.export,
  containComplaintPackageApi: mocks.contain,
}))

vi.mock('../../api/hold', () => ({ listHoldReasonsApi: mocks.reasons }))

vi.mock('../ui/Toast', () => ({
  useToast: () => ({ error: mocks.toastError, success: vi.fn(), info: vi.fn() }),
}))

vi.mock('../ui/ConfirmDialog', () => ({ useConfirm: () => async () => true }))

// Drawer 真实实现带 GSAP 动画与 portal，与本缺陷无关 → 换成最小容器，只保留 children + footer
vi.mock('../ui/Drawer', () => ({
  Drawer: ({ open, children, footer }: { open: boolean; children: ReactNode; footer?: ReactNode }) =>
    open ? (
      <div>
        <div>{children}</div>
        <div>{footer}</div>
      </div>
    ) : null,
}))

import { ComplaintPackageDrawer } from './ComplaintPackageDrawer'

const PREVIEW_VO = {
  anchorLotId: 100,
  anchorLotNo: 'LOT-A001',
  members: [
    { lotId: 100, lotNo: 'LOT-A001', relation: 'ANCHOR', depth: 0, qty: 25, status: 'wait' },
  ],
  memberCount: 1,
  truncated: false,
  summary: { activeHoldCount: 0, openAlarmCount: 0, scrapLotCount: 0 },
}

function renderDrawer(lotId: number | string = 100) {
  return render(
    <ComplaintPackageDrawer
      open
      onClose={() => {}}
      anchorLotId={lotId}
      anchorLotNo="LOT-A001"
      canBuild
      canContain={false}
    />,
  )
}

/** 驱动「预览影响面 → 生成追溯包」直到 built 态（下载按钮出现） */
async function toBuilt(user: UserEvent) {
  mocks.preview.mockResolvedValue(PREVIEW_VO)
  await user.click(screen.getByRole('button', { name: '预览影响面' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '生成追溯包' })).toBeEnabled())

  mocks.build.mockResolvedValue({ packageId: 999, packageNo: 'CP-20260923-1', status: 'READY' })
  await user.click(screen.getByRole('button', { name: '生成追溯包' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '下载 ZIP' })).toBeInTheDocument())
}

/** 可控的下载请求：返回未完成的 promise，由测试决定何时收尾 */
function deferredExports() {
  const resolvers: Array<() => void> = []
  mocks.export.mockImplementation(() => new Promise<void>((resolve) => resolvers.push(resolve)))
  return resolvers
}

describe('ComplaintPackageDrawer 下载重入闸（EVAL-0001）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('同一批次内连点两个下载按钮只发出 1 个请求，且在途时两钮都禁用', async () => {
    const user = userEvent.setup()
    renderDrawer()
    await toBuilt(user)
    const resolvers = deferredExports()

    // 复刻真实竞态窗口：两次点击发生在 React 提交之前（同一批次，
    // 此时 DOM 的 disabled 尚未更新），只有同步的 ref 闸门能挡住第二次点击。
    const jsonBtn = screen.getByRole('button', { name: '下载 JSON' })
    const zipBtn = screen.getByRole('button', { name: '下载 ZIP' })
    await act(async () => {
      jsonBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      zipBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(mocks.export).toHaveBeenCalledTimes(1)
    expect(mocks.export).toHaveBeenCalledWith(999, 'CP-20260923-1.json', 'json')
    expect(screen.getByRole('button', { name: '下载 ZIP' })).toBeDisabled()

    resolvers[0]()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '下载 ZIP' })).not.toBeDisabled(),
    )
    expect(mocks.toastError).not.toHaveBeenCalled()
  })

  it('抽屉重开后，旧会话请求的收尾不得清掉新会话的下载态', async () => {
    const user = userEvent.setup()
    const { rerender } = renderDrawer(100)
    await toBuilt(user)
    const resolvers = deferredExports()

    // 第一次下载在途
    await user.click(screen.getByRole('button', { name: '下载 JSON' }))
    expect(mocks.export).toHaveBeenCalledTimes(1)

    // 换锚点重开（模拟关闭抽屉后换另一个 Lot）
    rerender(
      <ComplaintPackageDrawer
        open
        onClose={() => {}}
        anchorLotId={200}
        anchorLotNo="LOT-B002"
        canBuild
        canContain={false}
      />,
    )
    await waitFor(() => expect(screen.getByRole('button', { name: '预览影响面' })).toBeInTheDocument())

    // 新会话重新生成并下载
    await toBuilt(user)
    await user.click(screen.getByRole('button', { name: '下载 ZIP' }))
    expect(mocks.export).toHaveBeenCalledTimes(2)
    // 在途时该钮文案为「处理中…」（Button 的 loading 态会替换 children）
    expect(screen.getByRole('button', { name: '处理中…' })).toBeDisabled()

    // 旧请求此刻才收尾 —— 不得影响新会话的下载态（仍在处理中）
    resolvers[0]()
    await waitFor(() => expect(mocks.export).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: '处理中…' })).toBeDisabled()

    // 新请求收尾后才恢复
    resolvers[1]()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '下载 ZIP' })).not.toBeDisabled(),
    )
  })
})
