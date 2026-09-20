---
type: 功能清单
module: Carrier
status: done
slices: []
aligns: []
updated: 2026-09-14
---

# MES 载具（Carrier）— C2 功能清单（现场扫码比对）

> 前提：Car-1～5（C0+C1）已齐；片级 Wafer、SECS Adapter、MCS / E87 **未齐**  
> 对齐：`MES-Carrier架构设计.md` §11 C2 · 一期清单 P1「现场扫码比对」· Track T2-3 闸之上  
> 业界对标：人工/半自动线 **MES 层 L2 读码比对**（非整厂 E87/RFID 联机）  
> 更新：2026-09-14  
> 状态：**Car-6/7/8 ✅（C2 闭环）**

---

## 0. 目标

交付 **TrackIn 扫码比对（L2）**：开工时校验「现场扫到的 CarrierCode」与「Lot 当前绑定」一致，防拿错盒。

原则：

- 比对真相只在 `CarrierFacade`；Track **只传扫码、只调断言**，不写绑定、不碰 Mapper
- 认 **binding 表** 为 SSOT（与 `assertBound` 一致）；不单信 `lot.carrier_id` 投影
- 闸可灰度：`mes.carrier.track-in-scan-required` 默认 **false**；关则与现网零行为差
- 与 `track-in-required` **正交叠加**：扫码闸开 ⇒ 隐含须已绑；未绑优先 `CARRIER_REQUIRED`
- 禁宣称 E87 / RFID Load Port / Carrier Management；禁 SlotMap；禁 MCS

产品口径：对外称「TrackIn 载具扫码比对 / ID verify（MES）」——**不**称 CMS / E87。

---

## 1. 架构约束（必须遵守）

| # | 约束 | 说明 |
|---|------|------|
| A1 | 高内聚 | 规范化扫码、查绑定、比等、抛错 **全部** 在 `CarrierFacade`（建议 `assertMatch(lotId, scannedCode)`） |
| A2 | 低耦合 | Track / Controller / 前端 **零** Carrier Mapper；不解析 id↔code（除非 Facade 已暴露） |
| A3 | 只读闸 | 比对路径禁止 bind/unbind/改态/写位置 |
| A4 | 同事务 | `assertMatch` 必须与 TrackIn **同一 `@Transactional`**；禁止先比后开新事务 |
| A5 | SSOT | 期望码 = 当前 binding → Carrier.`carrier_code`；binding 空 → 未绑 |
| A6 | 并发 | 见 §6；禁止「先查后开」跨请求缓存期望码 |
| A7 | 开关 | `enabled=false` → 扫码闸空操作（与 `assertBound` 同）；扫码闸默认 false |
| A8 | 演进口 | 入参保持 `String scannedCode`；日后 RFID/EAP 上报同一字段，规则不改 |

**禁止**

| # | 禁止 | 理由 |
|---|------|------|
| P1 | Track 内 `equals(lot.getCarrierId(), …)` 自比 | 投影漂移；旁路 Facade |
| P2 | 前端只藏按钮、后端不校验 | 可绕过；闸必须在服务端 |
| P3 | 扫码闸默认 true | 存量线挡车 |
| P4 | 引入 E87 状态机 / Load Port 读头依赖 | 范围绑架硬件；属 C5 |
| P5 | 比对失败静默改绑或自动换盒 | 过站与物流耦合；回滚语义乱 |
| P6 | 为 C2 新建 MCS/任务表 | 越界 |
| P7 | PUT Lot 改码「顶替扫码」 | 与一期禁 PUT 改 carrier 同构 |

---

## 2. 范围总览

| 优先级 | 能力 | 状态 |
|--------|------|------|
| P0 | 配置 `mes.carrier.track-in-scan-required`（默认 false） | ✅ |
| P0 | `CarrierFacade.assertMatch(lotId, scannedCode)` | ✅ |
| P0 | TrackIn 接线：DTO 收扫码 + 同事务调用 | ✅ |
| P0 | context：`carrierScanRequired`；已绑展示期望 `carrierCode` | ✅ |
| P0 | TrackIn `tx_log.extJson` 记 `scannedCarrierCode`（开闸且有扫时） | ✅ |
| P0 | 现场 Track 页：扫码输入（枪/框）；比对失败可读提示 | ✅ |
| P1 | 按线别/设备覆盖扫码闸（多租户配置） | 后置 |
| P2 | 设备 RFID → 同一 `assertMatch` | 随 Adapter/C5 |

---

## 3. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| Car-6 | 配置 + Facade `assertMatch` + 错误码 | ✅ |
| Car-7 | TrackIn DTO/Service/context/tx_log | ✅ |
| Car-8 | 现场 Track 扫码 UI + 提示文案 | ✅ |

顺序：**Car-6 → 7 → 8**。  
**禁止** Car-7 先于 Car-6（Track 自写比对）。  
**禁止** Car-8 只做前端校验、后端开关未接。

---

### Car-6（Facade 比对内核）✅

#### 6.1 交付

- 配置：`mes.carrier.track-in-scan-required` 默认 **false**（`application.yml` + 可读）✅
- `CarrierFacade.assertMatch(Long lotId, String scannedCode)` ✅
  - `carrier.enabled=false` → return  
  - `track-in-scan-required=false` → return（由 Track 侧也可短路；**Facade 内再防一层**）  
  - `lotId` 空 → `CARRIER_REQUIRED`  
  - 无 binding → `CARRIER_REQUIRED`  
  - `scannedCode` blank（trim 后）→ `CARRIER_SCAN_REQUIRED`  
  - trim 后与绑定 `carrier_code` **全等**（大小写敏感，与台账建档一致）→ 通过  
  - 否则 → `CARRIER_MISMATCH`
- **不**改表结构（沿用 `mes_carrier` / `mes_carrier_binding`）✅
- **本切片无** Track/HTTP/前端改动 ✅

落地：`CarrierFacade` · `CarrierFacadeImpl` · `application.yml`

#### 6.2 口径锁死

| 项 | 锁死 |
|----|------|
| 规范化 | 仅 `trim`；不做模糊匹配、不做忽略大小写（标签打印与台账必须一致） |
| 期望源 | binding → carrier 行的 `carrier_code`；禁止只读 `lot.carrier_id` 不 join |
| 与 assertBound | `assertMatch` 覆盖「未绑」；Track 扫码闸开时 **只调 assertMatch** 即可（不必再调 assertBound）。`track-in-required=true` 且扫码闸关：仍只 `assertBound` |
| 幂等 | 同码重复断言成功；无比对写库 |

#### 6.3 验收（Car-6）

- [x] Facade：`assertMatch` 未绑 / 空扫 / 错码 / 对码 / enabled 关 / scan 开关关
- [x] Facade 外无比对逻辑副本（Track 接线属 Car-7）

---

### Car-7（Track 接线）✅

#### 7.1 交付

- `TrackInDTO` 增加可选 `carrierCode`（扫码）；JSON 字段名与现场约定：`carrierCode` ✅
- `TrackService.trackIn`：当 `track-in-scan-required=true` → `carrierFacade.assertMatch(lotId, dto.carrierCode)`，与现有闸 **同事务、在改 Lot 状态之前** ✅
- 开关组合：

| track-in-required | track-in-scan-required | 行为 |
|-------------------|------------------------|------|
| false | false | 与现网一致 |
| true | false | 仅 `assertBound` |
| false | true | 仅 `assertMatch`（含未绑/空扫/错码） |
| true | true | `assertMatch` 即可（已含未绑） |

- context：`carrierScanRequired`（布尔，跟配置）；已有 `carrierCode` 继续暴露期望码供 UI 提示（**不**把期望码当扫码回填作弊）✅
- TrackIn `extJson`：扫码闸开且请求带扫码时写 `scannedCarrierCode`；比对失败不写成功履历 ✅
- Controller：透传 DTO；**不**在 Controller 比对 ✅

落地：`TrackInDTO` · `TrackService` / `TrackServiceImpl` · `TrackController` · `TrackContextVO`

#### 7.2 口径锁死

| 项 | 锁死 |
|----|------|
| Track 职责 | 传参 + 调 Facade；禁止拼装期望码、禁止查 carrier 表 |
| 失败 | 抛 `BusinessException`；整笔 TrackIn 回滚 |
| 成功履历 | 保留原 `carrier_id`；附加扫码字段便于稽核 |

#### 7.3 验收（Car-7）

- [x] 扫码闸关 + 不传码 → In 路径与现网一致（服务端已接）
- [x] 扫码闸开 → 调 `assertMatch`（空/错/对由 Facade 覆盖）
- [x] context 暴露 `carrierScanRequired`
- [x] 成功且闸开有扫 → ext 含 `scannedCarrierCode`
- [ ] 联调：错码 Lot 仍 WAIT（随 Car-8 / 现场验）

---

### Car-8（现场 UI）✅

#### 8.1 交付

- Track 现场页：当 `carrierScanRequired=true` 显示载具扫码框（支持扫码枪键盘楔入）✅
- 展示只读期望 `carrierCode`（有绑定时）；扫码框不自动填期望码 ✅
- 错误码映射可读文案：`CARRIER_SCAN_REQUIRED` / `CARRIER_MISMATCH` / `CARRIER_REQUIRED` ✅
- Admin 载具台账 **不**强制改；绑解流程保持 Car-5 ✅

落地：`TrackPage.tsx` · `web/src/api/track.ts`

#### 8.2 本切片不做

- 工业 PDA 专端、离线队列
- 设备侧 RFID 控件

#### 8.3 验收（Car-8）

- [x] 开关关：无扫码框，行为同现网
- [x] 开关开：无扫禁用开工按钮；服务端仍校验
- [x] 错码提示含「与绑定不一致」语义

---

## 4. 并发安全

| 场景 | 期望 | 机制 |
|------|------|------|
| 同 Lot 双人同时 TrackIn | 至多一笔成功 | Lot `updateById` 乐观锁（现网）；失败者刷新 |
| TrackIn 中另一事务解绑 | In 失败或乐观锁冲突 | 同事务内 `assertMatch` 读 binding；解绑改 `lot` 抬 version → In 更新行数 0 |
| TrackIn 中另一事务换绑 | 一期禁静默换绑；须先解后绑 | 双 UK + Facade 拒换绑；断言仍认当前 binding |
| 双人同时绑同盒 | 与 Car-3 相同 | 锁序 Lot→Carrier + UK |
| 客户端缓存旧期望码 | 禁止作比对依据 | 每次 In 服务端重读 binding |
| 重复提交同扫码 | 第二笔因状态非 WAIT 拒 | 现网 Track 状态机 |

**本阶段不强制** TrackIn 对 Lot `SELECT FOR UPDATE`（与现网 In 一致）；若压测出现「断言通过后、更新前」极端窗，再在 Car-7 补强为：扫码闸开时 `requireExecutableLot` 改行锁——**单独评审，不预支**。

比对路径 **不加** Carrier 行锁（只读）；避免与绑解锁序死锁。

---

## 5. 错误码（C2 新增）

| code | 切片 | 含义 |
|------|------|------|
| `CARRIER_SCAN_REQUIRED` | Car-6+ | 扫码闸开但未提供有效扫码 |
| `CARRIER_MISMATCH` | Car-6+ | 扫码与当前绑定 `carrier_code` 不一致 |

沿用：`CARRIER_REQUIRED` / `CARRIER_DISABLED` / `CARRIER_NOT_FOUND`（本闸不强制暴露 NOT_FOUND）。

---

## 6. 明确不做（C2）

| 项 | 说明 |
|----|------|
| E87 ID VERIFICATION 状态机 | C5 / Adapter |
| Load Port RFID 自动读 | 硬件 + EAP；入参形态预留即可 |
| SlotMap / ContentMap | C4 |
| 扫码自动 bind | 绑解仍走显式 API；防隐式换盒 |
| 忽略大小写 / 别名码 | 台账与标签治理问题，不进比对逻辑 |
| 按站别差异化规则表 | P1 配置后置 |
| 宣称「已支持 Carrier Management」 | 口径禁止 |

---

## 7. 依赖与接线

```
Car-6 assertMatch + 配置
  → Car-7 TrackIn 同事务调用 + context/tx_log
    → Car-8 现场扫码 UI
```

```
现场扫码 ──► TrackController ──► TrackService.trackIn
                                      └── CarrierFacade.assertMatch(lotId, scannedCode)
                                            └── 读 binding + carrier_code（只读）

禁止：Track → Carrier Mapper
禁止：assertMatch → TrackService / 改 Lot 工艺状态
禁止：前端期望码回填当扫码
```

---

## 8. 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `mes.carrier.enabled` | true | 模块总开关；false 时扫码闸空操作 |
| `mes.carrier.track-in-required` | false | L1 非空闸（已有） |
| `mes.carrier.track-in-scan-required` | **false** | L2 扫码比对闸（本清单） |

开闸建议：先 `track-in-required` 试点稳定 → 再开 `track-in-scan-required`；回滚只改配置。

---

## 9. 验收总表（架构级）

| # | 场景 | 期望 |
|---|------|------|
| B1 | 两闸均关 | 与 Car-5 现网一致 |
| B2 | 仅扫码闸开，已绑，对码 | TrackIn 成功 |
| B3 | 仅扫码闸开，已绑，错码 | `CARRIER_MISMATCH`；状态不变 |
| B4 | 仅扫码闸开，未绑 | `CARRIER_REQUIRED` |
| B5 | 仅扫码闸开，空扫 | `CARRIER_SCAN_REQUIRED` |
| B6 | enabled=false | 扫码闸不挡 |
| B7 | Track 包无 Carrier Mapper 引用 | 依赖检查 |
| B8 | 并发解绑 + In | 失败可解释；无脏开工 |

---

## 10. 文档入口

| 文档 | 路径 |
|------|------|
| 本清单 | `docs/模块/Carrier（载具）模块/MES-Carrier-C2功能清单.md` |
| 架构 | `MES-Carrier架构设计.md`（C2 = 现场扫码比对） |
| 一期 | `MES-Carrier一期功能清单.md` |
| WI | `WI-Carrier-01` / `WI-Carrier-02`（开闸后补扫码项） |
| Track | `MES-Track二期功能清单.md` §T2-3 |
