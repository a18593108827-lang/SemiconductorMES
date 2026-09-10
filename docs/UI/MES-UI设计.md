# MES UI 页面规范

依据：`PRODUCT.md` · `DESIGN.md`  
栈：React · 主题：Admin Light / Field Dark

## 1. 信息架构

| 区 | 页面 | 优先级 | 主题 |
|---|---|---|---|
| 登录 | 登录 / 顶号提示 | P0 | 随端 |
| 总览 | 生产看板 Dashboard | P0 | 可投屏深色 |
| Lot | 列表 · 详情 · Split/Merge/报废 | P0 | Admin |
| WIP | 在制查询 / 库存态 | P0 | Admin |
| Track | 现场执行台 Track In/Out | P0 | **Field** |
| Route | 路线 / Step 定义 | P0 | Admin |
| Equipment | 设备列表 · 状态 · 详情 | P0 | Admin |
| Hold | Hold/Release · 原因码 | P0 | Admin+Field |
| Alarm | 报警列表 · 实时条 | P1 | Admin+看板 |
| Recipe | 配方版本 | P1 | Admin |
| Dispatch | 派工建议 / 确认 | P1 | Admin+Field |
| Carrier | FOUP / Slot Map | P1 | Admin |
| EDC | 量测录入 | P1 | Field 优先 |
| History | 调查台（按日日志） | P0 | Admin |
| Report | 复盘页（Move/Hold）；导出后置 | ✅ 一期 | Admin |
| Auth | 用户 · 角色 · 权限 | P0 | Admin |

## 2. 壳层

**Admin Shell**  
顶栏 56 + 侧栏 240（可折至 56）+ 内容区 `padding: 16–24`。内容最大宽不限（表格全宽）。

**Field Shell**  
近黑全屏；顶条：工位/设备/操作员；主区大按钮+扫码；底或侧导航 ≤6 项。

**看板 Shell**  
无侧栏或极简；12 栏网格 gap 8–12；适合 1920+。

## 3. 关键页线框要点

### 3.1 Dashboard
- 首行：WIP 数、Hold 数、Alarm 数、稼动（中性底，禁止大色块英雄指标）
- 中：产线/设备状态矩阵（色+字母）
- 右或下：Alarm 流 + 产出趋势折线
- 实时：Steel Cyan live 点，可暂停

### 3.2 Lot 列表
- 筛选：状态、产品、时间、Eqp、Hold
- 表列：LotId(mono) · 状态 · 当前 Step · Eqp · 数量 · 更新时间 · 操作
- 行点击 → 右侧 Drawer 详情；批量 Hold/导出

### 3.3 Lot 详情 Drawer
- 头：LotId + 状态 pill + 主操作（Hold / Move…）
- Tab：概要 · 路线进度 · 履历 · 量测 · 附件
- 危险操作（报废/Merge）二次确认

### 3.4 Track 现场台（Field）
- 左：扫码/输入 Lot → 大字确认身份
- 中：当前 Step、允许事务大按钮 ≥48 高  
  - TrackIn / TrackOut / Hold  
  - Rework / Skip / Off-Flow  
  - **分批 / 合批 / 报废**（互斥展开内嵌面板；合批勾选同站候选；报废原因码必选 + `window.confirm`，文案明示不可撤销）
- 右：设备状态、Recipe 版本、最近履历
- 成功/失败全屏级反馈条，颜色+文字
- 面板显隐：GSAP `autoAlpha` + `y:6`，150–250ms，`prefers-reduced-motion` 瞬时

### 3.5 Equipment
- 列表+状态滤；详情：可用性、当前 Lot、能力、配方绑定
- 状态与 Dispatch/Track 只读一致，不在 UI 双写

### 3.6 Hold / Alarm
- Hold：原因码必选；Release 权限校验；列表可跳 Lot
- Alarm：级别排序；critical 顶栏常驻；确认/关闭走权限

### 3.7 History 追溯
- Admin `/app/history`：工具栏一行（按批次/设备、搜索、时间、事务码）；主区按日密排日志；异常行加重；点行开抽屉；SPLIT/MERGE 跳谱系
- ID / 时刻全程 Mono；禁止假圆点时间轴、禁止 9 列事务表
- 现场 Track 侧栏：本 Lot 近流水，不改

### 3.8 登录
- 极简；错误明确；已登录他处 → 顶号说明

## 4. 交互与动效

| 场景 | 行为 |
|---|---|
| 路由切换 | 无编排，直接内容 |
| Drawer | 200ms 滑入 power2.out |
| 状态变更 | pill 交叉淡入 150ms |
| 实时推送 | 行短暂高亮 1 次 |
| 表单提交 | 按钮 loading → toast |
| 扫码成功 | 短音可选 + 顶条成功（Field） |

## 5. 响应式

- Admin：≥1280 完整壳；768–1279 侧栏折叠；&lt;768 表横滑+底导航（非现场主场景）
- Field：以 1280×800 / 平板横屏为准；热区 ≥44

## 6. 交付检查

- [ ] 无 emoji 图标（Lucide）
- [ ] 可点击有 `cursor-pointer` + hover
- [ ] 对比 AA；状态非单靠色
- [ ] `prefers-reduced-motion`
- [ ] Admin / Field 主题切换不坏布局
- [ ] 375 / 768 / 1024 / 1440 无横溢（Field 可固定横屏）
