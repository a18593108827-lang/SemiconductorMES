# 半导 MES AI 赋能方案（考察稿）

> 版本：v0.1  
> 定位：与主 MES 解耦的 AI 能力规划，供选型与分期决策  
> 原则：AI 负责预警与建议，闭环动作走 MES（Track / Hold / Alarm），主路径不强依赖模型  
> 优先级：排在业务 MES 之后

---

## 1. 目标

在现有 MES（Lot / Track / EDC / Alarm / Hold / History / Dispatch）之上，用 AI 提升：

- 良率异常早发现、早拦截
- 质量根因定位效率
- 派工与设备风险辅助决策

**不做**：模型直接改 Lot 状态、自动改 Recipe 生效、绕过权限的自动 Scrap/Skip。

---

## 2. 总体架构

```
┌────────────── MES 业务域 ──────────────┐
│ Track · Hold · Alarm · Dispatch · EDC │
└───────┬──────────────────▲────────────┘
        │ 事件/API         │ 建议结果回写
        ▼                  │
┌───────────────────────────────────────┐
│           AI 赋能域（独立服务）          │
│  采集适配 → 特征层 → 模型推理 → 策略引擎 │
└───────┬──────────────────▲────────────┘
        │                  │
   特征/样本库          模型注册中心
   (数仓/OLAP)         (版本/灰度)
```

| 层级 | 职责 |
|------|------|
| 数据采集 | 订阅 MQ / 拉取 History、EDC、Eqp、Alarm |
| 特征层 | 宽表、窗口聚合、样本集 |
| 模型服务 | 训练、推理、评分 |
| 策略引擎 | 阈值 / 分数 → L1 看板 / L2 Alarm / L3 建议 Hold |
| 回写适配 | 调 MES API 建 Alarm、建「建议 Hold」、写 Report |

**约束**

1. TrackIn/TrackOut **同步链路不调 AI**
2. AI 结果经策略引擎，默认可人工确认
3. 每次决策记录：模型版本、特征快照、分数、动作

---

## 3. 场景清单

### 3.1 P0 — 良率检控

**输入**：Lot、产品、RouteStep、设备、Recipe、TrackOut、EDC、时间窗  

**能力**：基线监控（均值/σ/EWMA）+ 异常评分；分级 L1 黄警 → L2 Alarm → L3 建议 Hold  

**回写**：Alarm、建议 Hold（`Suggested` → 人工确认）、看板  

**考察指标**：告警准确率/误报率、从异常到 Hold 确认耗时

### 3.2 P0 — EDC / SPC 智能判异

SPC 规则保留为硬门槛；AI 作为增强层，捕捉多参数/跨站组合异常。

### 3.3 P1 — 低良率根因辅助

相似历史 Lot 检索 + 公共因子排序；展示在 Report / Lot「AI 洞察」，不定责、不自动改工艺。

### 3.4 P1 — 派工增强

规则 Dispatch 上叠加历史良率/负载/风险打分；规则否决权高于模型。

### 3.5 P2 — 设备健康预警 / 配方参数建议

健康预警进 Equipment；配方建议仅出草稿，强审批，禁止 AI 直接生效。

---

## 4. 良率检控详细设计

### 4.1 数据流

```
TrackOut / EDC 完成 → MQ → 特征作业 → 检测器 → 策略分级
  → 回写 Alarm / 建议 Hold → WebSocket 推看板
```

### 4.2 特征示例

yield_rate_roll_n、yield_vs_baseline、fail_bin_dist、eqp_id、recipe_ver、prev_step_edc_z、hold_rate_nearby

### 4.3 动作策略（默认）

| 级别 | 动作 |
|------|------|
| L1 | 看板黄警 |
| L2 | 建 Alarm + 通知 |
| L3 | 建议 Hold（人工确认） |
| L4（后期） | 白名单产品线自动 Hold + 审计 |

### 4.4 建议 Hold 状态机

```
AI Suggested → Engineer Confirmed（正式 Hold）
             → Dismissed（误报，回灌负样本）
             → Expired
```

---

## 5. 技术选型

| 方案 | 说明 |
|------|------|
| **独立 AI 服务（推荐）** | Python/FastAPI 或 Java，与 MES 分仓 |
| 先 XXL-JOB + 统计规则 | P0 起步可不训复杂模型 |
| RocketMQ / Redis / MinIO | 事件、特征缓存、模型文件 |

模型起步：统计控制图 → 异常检测 → 相似案例/监督模型。优先可解释、可审计。

---

## 6. 与 MES 模块关系

| MES 模块 | AI 回写 |
|----------|---------|
| Alarm | 创建质量/良率告警 |
| Hold | 建议 Hold / 确认后正式 Hold |
| Dispatch | 排序权重 |
| Report | 良率洞察、根因报告 |
| Track | **不直接调用** |
| Sa-Token | `ai:yield:view`、`ai:hold:confirm`、`ai:hold:dismiss` |

---

## 7. 接口与 Topic（草案）

| Topic / API | 说明 |
|-------------|------|
| `mes.ai.yield.alert` | 良率告警 |
| `mes.ai.hold.suggest` | 建议锁批 |
| `mes.ai.dispatch.score` | 设备分 |
| GET `/api/ai/yield/overview` | 良率总览 |
| POST `/api/ai/holds/{id}/confirm` | 确认建议 Hold |
| POST `/api/ai/holds/{id}/dismiss` | 驳回误报 |

---

## 8. 分期路线

| 阶段 | 内容 |
|------|------|
| A | History/EDC 标准化、良率宽表 |
| B | 统计基线 + L1/L2 告警 + 看板 |
| C | 建议 Hold + 确认流 + 决策日志 |
| D | 异常检测 + 根因相似检索 |
| E/F | Dispatch 打分、设备风险、配方建议 |

验收卡在 B/C：误报率不可接受则不上自动动作。

---

## 9. 风险与结论

| 风险 | 对策 |
|------|------|
| 误报乱 Hold | 默认建议制；白名单；阈值可配 |
| 拖慢 Track | AI 全异步 |
| 黑盒过不了审 | 先统计；附贡献因子 |

**结论**：先良率检控（统计）+ Alarm，再建议 Hold；AI 独立服务；可解释、可审计、可驳回优先于复杂模型。
