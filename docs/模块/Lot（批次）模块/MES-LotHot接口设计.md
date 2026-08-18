# Lot Hot / Priority — 架构设计

> 定位：急单信号与派工加权；**不改** qty / status / 工艺快照  
> 归属：**Lot 主数据属性**；**Dispatch 消费排序**；Track 不参与事务  
> 对齐：`半导MES架构设计.md` §5.5；`MES-Lot二期功能清单.md` §3.3；`MES-半导体业务清单.md` §4  
> 业界：Fab Priority Class + RTD；Hot / Super-Hot / Ultra-Hot（本期只做 Hot 档）  
> 前提：`mes_lot.priority` / `hot_flag` 已有；Lot API 已读写 Hot；WIP 已同步 `hot_flag` 排序  
> 更新：2026-08-10  
> 状态：HT-1～HT-3c / WIP 急度已闭环；**HT-4 争机延后**（见 §6.3）；交期/CR 后置  

---

## 0. 切片

| 切片 | 交付 | 状态 |
|------|------|------|
| HT-1 | 模型定稿：`priority` 主序 + `hot_flag` 显式特急 | ✅ |
| HT-2 | 已放行白名单：允许改 `priority` / `hot_flag`；禁改 product/qty/route | ✅ |
| HT-3 | Lot API：读写 + `GET /lots?hotFlag=`；打 Hot 抬 priority≥80 | ✅ |
| HT-3b | WIP 投影 `hot_flag` + 待派 `hot DESC, priority DESC` | ✅ `migrate_lot_hot.sql` |
| HT-3c | LotsPage Hot 开关 / 高亮 / 筛选；WIP 急度列 | ✅ |
| HT-4 | Dispatch 多 Lot 争机吃急度 | ⏸ **延后**（§6.3.1；现模型不必做） |
| HT-5 | 变更履历（可选） | ⏳ 后置 |
| HT-6 | Super/Ultra Hot（空等机台 / Hand-carry） | ❌ **默认不做** |
| HT-7 | 交期字段 + Critical Ratio 动态分 | ⏳ **有交期后再开** |

---

## 1. 目标与产品边界

**一句话：** Lot 声明「有多急」；Dispatch 按急度插队；不碰 Track 状态机。

| 做（本期已落地） | 不做 / 延后 |
|------------|------|
| `priority` 1–100（越大越急）为主排序键 | Super-Hot：下游机台空等 |
| `hot_flag` 0/1 显式特急（列表/看板强信号） | Ultra-Hot / Hand-carry 专人跟批 |
| 已放行仍可改急度（白名单） | 升 Hot 审批流 / 厂内配额管控 |
| WIP / Lot 列表按急度排序与高亮 | **HT-4** 多 Lot 争机自动排序（延后） |
| 列表 / 详情 Hot 高亮 | APS 长周期交期重排；自动按交期升降 priority |

---

## 2. 业界对齐（架构决策依据）

| 大厂常见 | 本系统映射 |
|----------|------------|
| Priority Class（多级，业务定档） | `priority` 1–100 |
| Hot Lot = 队首插队（合同/计划人工定） | `hot_flag=1`；Dispatch / WIP 提权 |
| Super/Ultra = 空等 / 专人 | **不做**；避免拖垮吞吐 |
| Critical Ratio / 交期紧迫度 | **后置**（HT-7）；本期无交期字段 |
| RTD 多因子规则表 | Dispatch / WIP 排序扩展 |

**结论：** 属性层用「数字优先级 + 布尔 Hot」对齐 SiView/Camstar/FAB300；派工闭环才算能力交付。  
**不算「大厂标准」的：** 打 Hot 把 `priority` 抬到 80——这是无交期时的过渡补丁，见 §2.1 / §9。

### 2.1 大厂怎么「算」急度（调研摘要）

大厂**不是**算 `max(priority, 80)`。常见两层：

**A. 业务定档（静态）**  
- Hot / P1 / 普通 / 工程批：由计划、客户合同、产品类型**人工或订单写入**  
- Hot 占比通常压到很低（个位数 %），否则普通批饿死  
- 档位本身一般**不由公式从 priority 抬出来**

**B. 派工算分（动态，真正计算）**  
机台前排队时 RTD（如 Applied Real-Time Dispatcher、FabTime Dispatch）多因子排序，常见含：

| 因子 | 含义 |
|------|------|
| Priority Class / Hot | 最高档先；盖过普通算分 |
| Critical Ratio (CR) | 交期紧迫度（见下） |
| EDD / 剩余等待 | 交期或 FIFO 变体 |
| 线平衡 / 瓶颈饥饿 | 全局 WIP 分布 |
| 换型 / Batch / Cascade | 本地吞吐 |

**Critical Ratio（文献常见写法之一）：**

```
CR = 剩余预计加工时间(TRPT) / 距交期剩余时间
```

- CR 越大 → 越赶不上 → 越优先（另有「越小越优先」的 CR 定义，实现时须统一约定）  
- 交期常由 Flow Factor 定：`Due = 进厂时间 + FF × RPT`（FF 常见约 2～3）

另有 fab 实践：先按线平衡档，再按 CR 细排（全局规则 + 本地规则混合）。

**对本系统的含义**

| 现在 | 后续（HT-7） |
|------|----------------|
| 无交期 → 无法上 CR | Lot 增加交期（或计划完工日） |
| `hot_flag` + `priority` + 地板 80 | 排序：`hot → CR → priority → …` |
| 不要为「更像大厂」去改地板数字 | 价值在交期/CR，不在把 80 改成 90 |

### 2.2 地板 80（`HOT_PRIORITY_FLOOR`）说明

实现：`applyHotPriorityFloor`——`hot_flag=1` 且 `priority<80` 时写库为 80；取消 Hot **不降** priority。

| 要解决的问题 | 不做的事 |
|--------------|----------|
| 避免「标了 Hot 但 priority=50」在只按数字排时仍靠后 | 代替 Priority Class 业务定档 |
| 无交期阶段的一致性补丁 | 代替 Critical Ratio |

**产品决策：本期保持；有 CR 后可删可留，优先做交期+CR，不优先调地板。**

---

## 3. 模块边界

```
Lot      = 存 priority / hot_flag；PUT 白名单可改；列表高亮
Dispatch = 选机过滤后排序吃急度（硬消费点）
Track    = 不因 Hot 改状态；TrackIn/Out 规则不变
WIP      = 投影同步 priority + hot_flag；待派按急度排序
Hold     = 与 Hot 正交；held 仍不可 Track，Hot 不解锁
History  = 可选写属性变更履历；不写 genealogy / 不写 split 类 tx
```

**禁止**

- 用 Hot 代替 Hold / Future Hold  
- 用改 `priority` 冒充交期承诺（交期字段后置）  
- Track 事务内因 Hot 跳过校验（禁跑、Hold、Recipe 资格仍硬拦）  
- 仅 UI 打标、Dispatch 不读（半截能力）

**原则**

- Hot 是**调度属性**，不是生命周期状态  
- 状态唯一真相仍在 Track；qty/status 禁走属性接口  
- 急度可随时调；工艺快照 / 产品码不可因急度改

---

## 4. 数据模型

| 字段 | 类型 | 规则 |
|------|------|------|
| `priority` | INT 1–100 | 默认 50；**越大越急**；主排序键 |
| `hot_flag` | TINYINT 0/1 | 默认 0；特急强信号 |

### 4.1 双字段关系（定稿）

| 规则 | 说明 |
|------|------|
| 独立可写 | 可只抬 `priority` 不加 Hot；也可 `hot_flag=1` |
| 推荐约定 | 打 Hot 时若 `priority < HOT_PRIORITY_FLOOR`，服务端抬到阈值（建议 **80**） |
| 取消 Hot | `hot_flag→0` **不自动降** priority（避免误伤人工调过的值） |
| 派生查询 | 看板「急单」= `hot_flag=1 OR priority≥80`（配置项，非硬编码死唯一） |

不采用「只用 priority、不要 hot_flag」：列表/现场需要一眼可辨的布尔标，与大厂 Hot 语义一致。  
不采用「只用 hot_flag」：丢失多级插队粒度，Dispatch 无法细排。

### 4.2 继承

| 事务 | 行为 |
|------|------|
| Split | 子 Lot **继承**父 `priority` / `hot_flag`（与现 Split 设计一致） |
| Merge | 主 Lot 保留自身急度；不一致仅告警（P0 已记软约束，不强制） |

### 4.3 DDL

`mes_lot.priority` / `hot_flag` 已存在。  
WIP：`mes_wip_lot.hot_flag`（`migrate_lot_hot.sql` / `schema.sql`）。

---

## 5. 权限与已放行白名单

| 操作 | 权限 | 放行后 |
|------|------|--------|
| 改 `priority` / `hot_flag` | `lot:edit` | **允许** |
| 改 `customer_lot` / `remark` | `lot:edit` | 允许（同白名单） |
| 改 `product_code` / `qty` / `route_*` | — | **拒绝**（qty 走 Track） |

升 Hot **不**新增独立权限码（二期）；厂规要审批时 P2 再加 `lot:hot`。

---

## 6. 接口

### 6.1 复用

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/lots/{id}` | body 含 `priority`、`hot_flag`；白名单校验 |
| GET | `/lots` · `/lots/{id}` | 回传两字段 |

### 6.2 行为

- `priority` 越界 → 400  
- `hot_flag` 非 0/1 → 400  
- 打 Hot 且 priority&lt;阈值 → 写库前抬到阈值（或返回明确 `priorityAdjusted`；实现二选一，建议静默抬并响应最终值）  
- **不写** `mes_lot_genealogy`；不改 status

### 6.3 Dispatch（消费契约）

**两层消费，勿混：**

| 层 | 场景 | 急度角色 | 现状 |
|----|------|----------|------|
| A. 选谁先动手 | WIP / Lot 待派列表 | `hot → priority` 排序 | ✅ 已落地 |
| B. 这批上哪台 | 单 Lot → 多机推荐 | 设备维：idle → 负载 → eqp_code | ✅ 保持（**不吃** Lot 急度） |
| C. 多批争一台 | 同机队列自动排谁先上 | Lot 急度盖过普通批 | ⏸ HT-4 延后 |

HT-4（层 C）目标排序键（开做时用）：

| 序 | 键 | 方向 |
|----|-----|------|
| 1 | `hot_flag` | 1 优先 |
| 2 | `priority` | 大优先 |
| 3 | 其余（等待时间 / CR 等，后置） | — |

单 Lot 选机（层 B）**不要**把 Lot 急度塞进设备排序，否则 Hot 会永久偏置负载均衡。

#### 6.3.1 HT-4 产品决策（2026-08-10）

**结论：现在不做。**

| 依据 | 说明 |
|------|------|
| 现交互 | 「先选 Lot → 再推荐机台」；急度已在 WIP/Lot 列表体现，人选谁先派谁 |
| 无争机面 | 尚无「按机台看待派队列、系统决定先派谁」的 UI/API |
| 半截风险低 | 层 A 已闭环，不等于「只标不派」；层 C 是另一交互形态 |

**何时开 HT-4（满足任一）：**

1. 出现「设备待派队列 / What-Next」：同机多 Lot 列表由系统排序  
2. 自动派工 / 批量 Reserve：系统替操作员决定先后  
3. 现场明确要求：同站多批时 Hot 必须压过人工点选顺序  

开做时：Dispatch 页标注急度 + 争机列表 `ORDER BY hot_flag DESC, priority DESC`；单 Lot 选机排序不变。

---

## 7. 前端

| 页 | 改动 | 状态 |
|----|------|------|
| LotsPage 列表 | Hot 标 / 高亮；可筛 `hot` | ✅ |
| LotsPage 编辑 | `hot_flag` 开关；打 Hot 抬 priority 提示 | ✅ |
| WIP | 急度列 + Hot 行高亮；列表急度排序 | ✅ |
| Track | 可选展示 Hot（非阻塞） | ⏳ |
| Dispatch 页 | 争机队列急度（依赖 HT-4） | ⏸ 延后 |

---

## 8. 验收

1. 已放行 Lot 可改 `priority` / `hot_flag`；改 `qty` 仍失败  
2. `hot_flag=1` 列表可辨；取消 Hot 不强制改 priority  
3. 打 Hot 时 priority 不低于阈值  
4. Split 子批继承急度  
5. WIP / 待派列表：Hot > 高 priority > 普通（✅）；多 Lot 争机同序（⏸ HT-4）  
6. Hold / 禁跑 / Recipe 不匹配时，Hot **不能**绕过  
7. 不产生虚假 genealogy / scrap / bonus 履历  

---

## 9. 风险与演进

| 风险 | 应对 |
|------|------|
| 全厂滥打 Hot → 普通批饿死 | P2：配额 / 审批 / 看板「Hot 占比」 |
| 误以为「没做 HT-4 = 半截」 | WIP 急度已消费；HT-4 仅争机形态需要，见 §6.3.1 |
| 与交期模型冲突 | 见 §9.1；Hot 仍最高档 |
| Ultra 空等 | **默认不做**；除非客户合同写死加速档 |

### 9.1 演进路线（产品决策）

| 阶段 | 做什么 | 不做什么 |
|------|--------|----------|
| **现在** | `hot_flag` + priority + 地板 80 + Lot/WIP UI | 不要为「完整」强上 HT-4；不要改地板算法 |
| **HT-4 触发时** | 机台待派队列 / 自动派工吃急度 | 不要改单 Lot 选机的设备维排序 |
| **下一步** | Lot 交期 + Dispatch/WIP 插 CR | 不要先上 Super/Ultra |
| **有 CR 后** | 排序 `hot → CR → priority → …`；地板可删可留 | 不要指望只调 80→90 |
| **远期** | Hot 配额/审批；APS 交期回写 | Hand-carry / 空等机台默认不上 |

**一句话：** 本期 Hot 能力在 Lot/WIP 已闭环；HT-4 等「按机排队」交互再开；对齐大厂下一步是交期+CR，不是争机或改地板。

---

## 10. 关联

- `MES-Lot二期功能清单.md` §3.3 / §3.4 / L2-5  
- `MES-Lot数据库设计.md` §1  
- `MES-Lot功能文档.md`  
- `MES-Dispatch功能文档.md` §4.2  
- `MES-半导体业务清单.md` §4  
- `半导MES架构设计.md` §5.5  
