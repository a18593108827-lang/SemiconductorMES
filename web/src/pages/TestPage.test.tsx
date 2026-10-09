import '@testing-library/jest-dom/vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * TD-1 步骤 f / EVAL-0001 口径：登记表单同步重入闸。
 * createGate 在 listLots / create 异步之前同步置位，连点不得发出第二次登记。
 */
const mocks = vi.hoisted(() => ({
  listRecords: vi.fn(),
  createRecord: vi.fn(),
  listLots: vi.fn(),
  listBins: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}))

vi.mock('../api/test', () => ({
  listTestRecordsApi: mocks.listRecords,
  getTestRecordApi: vi.fn(),
  listTestRecordsByLotApi: vi.fn(),
  createTestRecordApi: mocks.createRecord,
  voidTestRecordApi: vi.fn(),
  listTestBinsApi: mocks.listBins,
  createTestBinApi: vi.fn(),
  updateTestBinApi: vi.fn(),
}))

vi.mock('../api/lot', () => ({
  listLotsApi: mocks.listLots,
}))

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    hasPermission: (code: string) =>
      ['test:view', 'test:create', 'test:void', 'test:edit-bin'].includes(code),
  }),
}))

vi.mock('../components/ui/Toast', () => ({
  useToast: () => ({ success: mocks.toastSuccess, error: mocks.toastError, info: vi.fn() }),
}))

vi.mock('../components/ui/ConfirmDialog', () => ({ useConfirm: () => async () => true }))

vi.mock('../components/ui/Drawer', () => ({
  Drawer: ({
    open,
    children,
    footer,
  }: {
    open: boolean
    children: ReactNode
    footer?: ReactNode
  }) =>
    open ? (
      <div>
        <div>{children}</div>
        <div>{footer}</div>
      </div>
    ) : null,
}))

vi.mock('gsap', () => ({
  default: {
    context: () => ({ revert: () => {} }),
    from: () => {},
    fromTo: () => {},
    to: () => {},
    set: () => {},
  },
}))

vi.mock('../lib/motion', () => ({ motionMs: () => 0 }))

import { TestPage } from './TestPage'

describe('TestPage 登记重入闸（TD-1 / EVAL-0001）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listRecords.mockResolvedValue({ records: [], total: 0 })
    mocks.listBins.mockResolvedValue([])
  })

  it('连点提交只发出一次 create（同步闸挡住第二次）', async () => {
    const user = userEvent.setup()
    render(<TestPage />)

    await waitFor(() => expect(mocks.listRecords).toHaveBeenCalled())
    await user.click(screen.getAllByRole('button', { name: /登记记录/ })[0])

    const lotNo = document.getElementById('lotNo') as HTMLInputElement
    const programName = document.getElementById('programName') as HTMLInputElement
    const programVersion = document.getElementById('programVersion') as HTMLInputElement
    const totalQty = document.getElementById('totalQty') as HTMLInputElement
    await user.type(lotNo, 'LOT-T001')
    await user.type(programName, 'PROG')
    await user.type(programVersion, '1.0')
    await user.clear(totalQty)
    await user.type(totalQty, '25')

    const binInputs = screen.getAllByPlaceholderText('档号')
    const qtyInputs = screen.getAllByPlaceholderText('颗数')
    await user.type(binInputs[0], '1')
    await user.type(qtyInputs[0], '25')

    let resolveLots!: (v: unknown) => void
    mocks.listLots.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLots = resolve
        }),
    )
    mocks.createRecord.mockResolvedValue({
      id: 1,
      recordNo: 'TR-20261009-001',
      lotId: 10,
      lotNo: 'LOT-T001',
      testStage: 'FT',
      programName: 'PROG',
      programVersion: '1.0',
      eqpId: null,
      eqpCode: null,
      testTime: '2026-10-09T12:00:00',
      totalQty: 25,
      sourceType: 'MANUAL',
      sourceRef: null,
      remark: null,
      createTime: null,
      bins: [],
    })

    const submit = screen.getByRole('button', { name: '提交' })
    await act(async () => {
      submit.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      submit.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(mocks.listLots).toHaveBeenCalledTimes(1)

    resolveLots({
      records: [{ id: 10, lotNo: 'LOT-T001', status: 'wait', version: 1 }],
      total: 1,
    })

    await waitFor(() => expect(mocks.createRecord).toHaveBeenCalledTimes(1))
  })
})
