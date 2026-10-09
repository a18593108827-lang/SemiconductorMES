import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import {
  createLotCustomerMapApi,
  createLotStripsApi,
  listLotCustomerMapsApi,
  listLotStripsApi,
  type LotCustomerMapItem,
  type LotStripItem,
  type MesLotStatus,
} from '../../api/lot'
import { listTestRecordsByLotApi, type TestRecordItem } from '../../api/test'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { useToast } from '../ui/Toast'
import { ApiError } from '../../lib/http'

function fmtTime(v: string | null | undefined) {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 16)
}

const STAGE: Record<string, string> = { CP: '晶圆测试', FT: '成品测试', OTHER: '其他' }

export function LotTestMappingSection({
  lotId,
  status,
  canViewTest,
  canEditLot,
}: {
  lotId: number | string
  status: MesLotStatus
  canViewTest: boolean
  canEditLot: boolean
}) {
  const toast = useToast()
  const writable = status !== 'merged' && status !== 'scrapped'
  const [tests, setTests] = useState<TestRecordItem[]>([])
  const [testLoading, setTestLoading] = useState(false)
  const [strips, setStrips] = useState<LotStripItem[]>([])
  const [maps, setMaps] = useState<LotCustomerMapItem[]>([])
  const [pkgLoading, setPkgLoading] = useState(false)
  const [stripText, setStripText] = useState('')
  const [savingStrip, setSavingStrip] = useState(false)
  const stripGate = useRef(false)
  const [mapType, setMapType] = useState<'INBOUND' | 'OUTBOUND'>('INBOUND')
  const [externalLotNo, setExternalLotNo] = useState('')
  const [savingMap, setSavingMap] = useState(false)
  const mapGate = useRef(false)

  const loadTests = useCallback(async () => {
    if (!canViewTest) return
    setTestLoading(true)
    try {
      setTests(await listTestRecordsByLotApi(lotId))
    } catch (err) {
      setTests([])
      toast.error(err instanceof ApiError ? err.message : '测试结果加载失败')
    } finally {
      setTestLoading(false)
    }
  }, [canViewTest, lotId, toast])

  const loadPkg = useCallback(async () => {
    setPkgLoading(true)
    try {
      const [s, m] = await Promise.all([listLotStripsApi(lotId), listLotCustomerMapsApi(lotId)])
      setStrips(s)
      setMaps(m)
    } catch (err) {
      setStrips([])
      setMaps([])
      toast.error(err instanceof ApiError ? err.message : '条级 / 映射加载失败')
    } finally {
      setPkgLoading(false)
    }
  }, [lotId, toast])

  useEffect(() => {
    void loadTests()
    void loadPkg()
  }, [loadTests, loadPkg])

  async function submitStrips() {
    if (stripGate.current || !canEditLot || !writable) return
    const nos = stripText
      .split(/[\n,]+/)
      .map((s) => s.trim())
      .filter(Boolean)
    if (nos.length === 0) {
      toast.error('请填写条号，一行一条或逗号分隔')
      return
    }
    stripGate.current = true
    setSavingStrip(true)
    try {
      await createLotStripsApi(
        lotId,
        nos.map((stripNo, i) => ({ stripNo, seqNo: i + 1 })),
      )
      toast.success(`已登记 ${nos.length} 条`)
      setStripText('')
      await loadPkg()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : 'Strip 登记失败')
    } finally {
      stripGate.current = false
      setSavingStrip(false)
    }
  }

  async function submitMap() {
    if (mapGate.current || !canEditLot || !writable) return
    if (!externalLotNo.trim()) {
      toast.error('外部批号不能为空')
      return
    }
    mapGate.current = true
    setSavingMap(true)
    try {
      await createLotCustomerMapApi(lotId, {
        mapType,
        externalLotNo: externalLotNo.trim(),
      })
      toast.success('已登记客户映射')
      setExternalLotNo('')
      await loadPkg()
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : '映射登记失败')
    } finally {
      mapGate.current = false
      setSavingMap(false)
    }
  }

  return (
    <div className="space-y-4 border-t border-border pt-4">
      {canViewTest ? (
        <section className="space-y-2">
          <h3 className="text-sm font-semibold">测试结果</h3>
          {testLoading ? (
            <p className="text-sm text-muted">加载中…</p>
          ) : tests.length === 0 ? (
            <p className="text-sm text-muted">本批尚无测试记录</p>
          ) : (
            <ul className="space-y-1.5">
              {tests.slice(0, 8).map((r) => (
                <li key={String(r.id)}>
                  <Link
                    to="/app/test"
                    className="flex flex-wrap items-baseline gap-x-2 rounded-md border border-border bg-surface px-2.5 py-1.5 text-sm hover:bg-border/30"
                  >
                    <span className="font-mono text-[13px] text-accent">{r.recordNo}</span>
                    <span className="text-xs text-muted">{STAGE[r.testStage] ?? r.testStage}</span>
                    <span className="font-mono text-[12px] text-muted">{fmtTime(r.testTime)}</span>
                    <span className="ml-auto font-mono text-[13px]">{r.totalQty} 颗</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">Mapping / 条级</h3>
        {pkgLoading ? <p className="text-sm text-muted">加载中…</p> : null}
        <p className="text-xs font-medium text-muted">Strip</p>
        {strips.length === 0 && !pkgLoading ? (
          <p className="text-sm text-muted">尚未登记条号</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface text-xs text-muted">
                <tr className="h-8 border-b border-border">
                  <th className="px-2">序</th>
                  <th className="px-2">条号</th>
                  <th className="px-2">颗数</th>
                </tr>
              </thead>
              <tbody>
                {strips.map((s) => (
                  <tr key={String(s.id)} className="h-8 border-b border-border">
                    <td className="px-2 font-mono text-[12px] text-muted">{s.seqNo ?? '—'}</td>
                    <td className="px-2 font-mono text-[13px]">{s.stripNo}</td>
                    <td className="px-2 font-mono text-[13px]">{s.dieQty ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {canEditLot && writable ? (
          <div className="space-y-1.5">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted">登记条号（一行一条）</span>
              <textarea
                className="min-h-[72px] w-full rounded-md border border-border bg-bg px-3 py-2 font-mono text-[13px]"
                value={stripText}
                onChange={(e) => setStripText(e.target.value)}
                placeholder={'ST-001\nST-002'}
              />
            </label>
            <Button loading={savingStrip} onClick={() => void submitStrips()}>
              <Plus className="size-4" aria-hidden />
              登记 Strip
            </Button>
          </div>
        ) : null}

        <p className="pt-2 text-xs font-medium text-muted">客户映射</p>
        {maps.length === 0 && !pkgLoading ? (
          <p className="text-sm text-muted">尚未登记来料 / 出货批号</p>
        ) : (
          <div className="overflow-x-auto rounded-md border border-border">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface text-xs text-muted">
                <tr className="h-8 border-b border-border">
                  <th className="px-2">方向</th>
                  <th className="px-2">外部批号</th>
                </tr>
              </thead>
              <tbody>
                {maps.map((m) => (
                  <tr key={String(m.id)} className="h-8 border-b border-border">
                    <td className="px-2 text-xs">{m.mapType === 'INBOUND' ? '来料' : '出货'}</td>
                    <td className="px-2 font-mono text-[13px]">{m.externalLotNo}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {canEditLot && writable ? (
          <div className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col gap-1.5 text-sm">
              <span className="text-xs font-medium text-muted">方向</span>
              <select
                className="h-9 rounded-md border border-border bg-bg px-2 text-sm"
                value={mapType}
                onChange={(e) => setMapType(e.target.value as 'INBOUND' | 'OUTBOUND')}
              >
                <option value="INBOUND">来料</option>
                <option value="OUTBOUND">出货</option>
              </select>
            </label>
            <Field
              label="外部批号"
              name="extLot"
              className="font-mono"
              value={externalLotNo}
              onChange={(e) => setExternalLotNo(e.target.value)}
            />
            <Button loading={savingMap} onClick={() => void submitMap()}>
              登记映射
            </Button>
          </div>
        ) : null}
      </section>
    </div>
  )
}
