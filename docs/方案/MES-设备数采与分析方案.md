---
type: 方案
module: 
status: draft
slices: []
aligns: []
updated: 
---

# 半导 MES 设备数据采集与分析方案

> 版本：v0.1（考察稿）  
> 定位：设备连续量（温、压等）采集、阈值/异常检测、实时大屏、趋势分析  
> 原则：高频时序进时序库；告警归 Alarm；大屏只读；不写入 Track 主路径  
> 模块建议名：Equipment Data / Dashboard（设备数采与看板）

---

## 1. 目标

| 能力 | 说明 |
|------|------|
| 温度 / 压力等采集 | 连续过程量采集与存储 |
| 报警检测 | 超限、突变、持续异常 → Alarm |
| 实时大屏 | 产线 / 设备状态与关键量实时展示 |
| 异常趋势分析 | 班次/日维度趋势、关联 Lot/设备健康 |

**非目标**：替代 SECS/GEM；数采直接 TrackIn/Out；MySQL 存高频点。

---

## 2. 与现有体系关系

```
SECS/GEM Adapter ──► Equipment 状态 / 事件
传感器 / PLC / OPC ─► 数采网关 ──► 时序库
                              ├─► 规则检测 ──► Alarm
                              ├─► Redis 热数据 ──► 大屏 (WebSocket)
                              └─► 特征 / 聚合 ──► AI · Report
```

| 已有模块 | 关系 |
|----------|------|
| Equipment | 主数据；测点挂 eqpId |
| Alarm | 统一告警入口 |
| Hold | 仅策略/人工触发，数采不直连 |
| Track | 可选关联加工时段曲线，不参与事务 |

---

## 3. 总体架构

```
采集层（OPC-UA / Modbus / MQTT / 厂商 SDK，边缘可部署）
  → 时序库（原始点）+ Redis（最新值）+ 规则引擎 + 聚合作业
  → Alarm 适配 / 大屏 API / 特征供数 API
```

---

## 4. 采集设计

### 测点模型

pointId、eqpId、chamberId、metric（temperature/pressure…）、unit、sampleRate、tags

### 消息示例

```json
{
  "eqpId": "EQP-CVD-01",
  "pointId": "PT-TEMP-01",
  "metric": "temperature",
  "value": 425.6,
  "unit": "C",
  "ts": 1710000000123,
  "quality": "GOOD"
}
```

原则：边缘可缓冲补传；BAD 质量位单独策略；连续量与 SECS 事件分离。

---

## 5. 报警检测

| 规则 | 说明 |
|------|------|
| 高高 / 高低限 | 绝对阈值 |
| 速率 roc | 变化过快 |
| 持续超限 | 连续 N 秒防抖 |
| 采样中断 | 超时无数据 |

流程：匹配 → 冷却抑制 → Alarm API/MQ → WebSocket。  
`source = EQUIPMENT_DATA`；规则优先于 AI 模型。

---

## 6. 实时大屏

- 产线总览、设备卡片（温压最新值 + sparkline）、告警滚动、近窗曲线  
- Redis 最新值；推送合并 500ms～1s；按产线分区订阅  
- 权限：`dashboard:view`；只读

---

## 7. 异常趋势分析

聚合：avg/max/min/p95（1m/5m/1h）、超限次数、基线偏离、报警密度。  

场景：单设备漂移、批内曲线（TrackIn～Out）、同型机台对比、报警趋势。

---

## 8. 存储选型

| 存储 | 数据 |
|------|------|
| 时序库（InfluxDB / TDengine / Timescale 等） | 原始与降采样 |
| Redis | 最新值、大屏 |
| MySQL | 测点定义、规则、日聚合（可选） |
| RocketMQ | 测点流（可选）、告警事件 |

保留示例：原始 7～30 天；1m 聚合 90 天；1h 聚合 1～2 年。

---

## 9. 服务边界

独立服务/包：`equipment-data`（point / ingest / rule / query / screen / bridge）。  
**不进主业务 Spring Boot 同进程长期混部**（可后期拆；高频写入隔离）。

---

## 10. 接口草案

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/eqdata/points | 注册测点 |
| POST | /api/eqdata/rules | 配置规则 |
| GET | /api/eqdata/eqps/{id}/latest | 最新值 |
| GET | /api/eqdata/eqps/{id}/series | 趋势 |
| GET | /api/eqdata/lots/{id}/series | 批内曲线 |
| GET | /api/eqdata/screens/{lineId} | 大屏快照 |
| POST | /api/eqdata/ingest | 网关上报 |

权限：`eqdata:point:manage`、`eqdata:query`、`dashboard:view`、`eqdata:ingest`

---

## 11. 分期

| 阶段 | 内容 |
|------|------|
| D0 | 测点 + 网关接入 1～2 台试点 |
| D1 | 阈值规则 + Alarm + Redis 最新值 |
| D2 | 产线实时大屏 |
| D3 | 降采样趋势 + 批内曲线 |
| D4 | 特征供 AI / 设备健康 |

---

## 12. 风险与结论

| 风险 | 对策 |
|------|------|
| 打爆 MySQL | 禁止高频点进业务库 |
| 告警风暴 | 冷却、持续时长、合并 |
| 大屏卡顿 | 降采样、推送合并 |
| 与 Adapter 职责不清 | 事件 vs 连续量分开 |

**结论**：独立数采与分析域；采集→时序库→规则告警→大屏为最小闭环；告警统一进 Alarm。
