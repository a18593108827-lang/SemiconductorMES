<!-- SEED -->
---
name: MES
description: 半导体制造执行系统 — 管理端浅色 + 现场台深色
colors:
  primary: "oklch(0.49 0.18 28)"
  accent: "oklch(0.58 0.12 230)"
  success: "oklch(0.62 0.14 145)"
  warning: "oklch(0.78 0.14 75)"
  danger: "oklch(0.55 0.20 25)"
  bg: "oklch(1 0 0)"
  surface: "oklch(0.97 0 0)"
  surface-raised: "oklch(1 0 0)"
  border: "oklch(0.90 0 0)"
  ink: "oklch(0.22 0.015 28)"
  muted: "oklch(0.48 0.01 28)"
  field-bg: "oklch(0.10 0 0)"
  field-surface: "oklch(0.16 0 0)"
  field-border: "oklch(0.28 0 0)"
  field-ink: "oklch(0.93 0 0)"
  field-muted: "oklch(0.65 0 0)"
typography:
  title:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: 1.3
  mono:
    fontFamily: "IBM Plex Mono, ui-monospace, monospace"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: 1.35
rounded:
  sm: "4px"
  md: "6px"
  lg: "8px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "oklch(1 0 0)"
    rounded: "{rounded.md}"
    padding: "8px 14px"
    height: "36px"
  button-primary-field:
    backgroundColor: "{colors.primary}"
    textColor: "oklch(1 0 0)"
    rounded: "{rounded.md}"
    padding: "12px 20px"
    height: "48px"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "8px 14px"
    height: "36px"
  input:
    backgroundColor: "{colors.bg}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    height: "36px"
    padding: "8px 12px"
  table-header:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted}"
    height: "40px"
  status-pill:
    rounded: "{rounded.sm}"
    padding: "2px 8px"
    height: "22px"
---

# Design System: MES

## Overview

**Creative North Star: "Tempered Steel Cleanroom"**

高效、硬核、现代的制造执行工具。管理端像 Linear：浅底、清晰层级、表格优先；现场台像 Grafana 值班屏：近黑底、大触控、状态灯可读；表单密度参考企业后台，但不堆装饰。

拒绝：紫渐变 SaaS、奶油底、玻璃大卡片、emoji 图标、入场编排动画。

**Key Characteristics:**
- 双主题：Admin Light / Field Dark，同一 token 体系
- Restrained：主色 ≤10% 屏面积，留给 CTA 与选中态
- 状态语义色独立于品牌色；色+文案+图标三重编码
- 动效 150–250ms，只表达状态变化
- 图标：Lucide，统一 16/20/24

## Colors

工业冷静面 + 锻红主操作 + 钢青实时信号。策略：**Restrained**。

### Primary
- **Forge Red** `oklch(0.49 0.18 28)`：主按钮、当前选中、关键路径 CTA。填充上文字一律白。

### Secondary
- **Steel Cyan** `oklch(0.58 0.12 230)`：实时/在线、链接、图表主系列、WebSocket 活动指示。

### Tertiary（语义，非装饰）
- **Run Green** `oklch(0.62 0.14 145)`：Running / TrackIn / 正常
- **Hold Amber** `oklch(0.78 0.14 75)`：Hold / Warning
- **Alarm Crimson** `oklch(0.55 0.20 25)`：Alarm / Error / Scrap（可与 Primary 同色相，L/C 略抬以区分「危险态」）

### Neutral — Admin Light
- **bg** `oklch(1 0 0)` · **surface** `oklch(0.97 0 0)` · **border** `oklch(0.90 0 0)`
- **ink** `oklch(0.22 0.015 28)` · **muted** `oklch(0.48 0.01 28)`

### Neutral — Field Dark
- **field-bg** `oklch(0.10 0 0)` · **field-surface** `oklch(0.16 0 0)` · **field-border** `oklch(0.28 0 0)`
- **field-ink** `oklch(0.93 0 0)` · **field-muted** `oklch(0.65 0 0)`

**The 10% Accent Rule.** Primary/Accent 合计不超过可视面积约 10%。KPI 禁止大块品牌色底。

**The Triple Status Rule.** 状态必须同时有：色点或底、短标签文案、图标（可选但 Alarm/Hold 必有）。

## Typography

**Display/Body/Label Font:** IBM Plex Sans  
**Mono Font:** IBM Plex Mono（LotId、EqpId、Recipe 版本、时间戳）

产品 UI 单一家族；比例约 1.15。不用流体大标题。

### Hierarchy
- **Title** 600 / 20px / 1.3：页标题、抽屉标题
- **Body** 400 / 14px / 1.45：说明、表单、单元格默认
- **Label** 500 / 12px / 1.3：字段名、表头、角标
- **Mono** 500 / 13px / 1.35：业务 ID、数值列

现场台：Body 升至 16px，主操作按钮字重 600。

**The ID Mono Rule.** 所有业务主键列用 Mono，禁止混用展示字体。

## Elevation

默认扁平。深度靠 **surface 色阶 + 1px border**，不用大阴影墙。

### Shadow Vocabulary
- **rest:** none
- **overlay** `0 8px 24px oklch(0 0 0 / 0.12)`：Modal / Drawer（Admin）
- **field-overlay** `0 8px 28px oklch(0 0 0 / 0.45)`：现场浮层

**The Flat-By-Default Rule.** 表格行、筛选条、侧栏无投影；仅浮层抬起。

## Components

### Buttons
- 圆角 6px；Admin 高 36；Field 高 48，最小宽 88
- Primary：Forge Red + 白字；Hover 略降 L；Disabled 降 chroma + 降对比
- Secondary：surface + ink + border；Ghost：透明 + ink
- Danger：Alarm 语义色；不可逆操作需确认（抽屉内，不优先 Modal）
- 状态：default / hover / focus-ring(accent 2px) / active / disabled / loading

### Inputs
- 高 36（Field 44）；1px border；focus 用 accent ring
- 必有可见 label；禁止 placeholder-only
- 校验错误：danger 边框 + 下方文案

### Tables（核心）
- 行高 Admin 40 / Field 52；表头 sticky；斑马可选极淡 surface
- 列：勾选 | 业务 ID(mono) | 状态 pill | 关键字段 | 操作
- 支持多选 + 批量操作条；横向溢出 `overflow-x-auto`
- 空态：一句下一步操作 + 主按钮（如「创建 Lot」）
- 加载：行骨架，不用整页大 spinner

### Status Pill
- 高 22；字 12 medium；左 6px 色点 + 文案
- 映射：Idle/Queued 中性 · Running 绿 · Hold 琥珀 · Alarm/Scrap 红 · Offline 灰

### Navigation
- Admin：左侧 240 可折叠 + 顶栏 56（面包屑、全局搜、告警铃、用户）
- Field：底栏或左侧大图标导航，项 ≤6；当前项 accent 底或左边 2px（整边指示，不用装饰侧条卡片）
- 全局命令：`⌘K` / `Ctrl+K` 搜 Lot / Eqp / Recipe

### Overlays
- Drawer 分级：`sm` 440（≤6 字段）/ `md` 560（中等详情）/ `lg` 720（宽只读）；多表可编辑配置用 **全页工作台**（如 Route 维护），勿塞抽屉
- 优先 **Drawer** 做短表单与轻详情；Modal 仅用于短确认与阻塞告警
- Toast 右上；Alarm 推送可固定顶条，critical 不自动消失

### Charts（看板）
- 产出趋势：折线/面积；设备状态：色块矩阵；报警：流式区或 ticker
- 库：Recharts 或 ECharts；色盲加线型/图案
- Live 点：Steel Cyan pulse，提供暂停

### Motion（GSAP 可用场景）
- 允许：状态切换 crossfade、抽屉滑入、表格行高亮闪一下、数字微增
- 禁止：页面入场 stagger 大秀、bounce/elastic、无限装饰 pulse（live 点除外且可关）
- `prefers-reduced-motion: reduce` → 瞬时或 0 时长 crossfade
- 时长 150–250ms，ease-out（power2.out）

## Do's and Don'ts

### Do
- 管理端浅色中密；现场台深色大触控；同一组件 API
- 表格 + 筛选 + 抽屉完成 80% 业务
- Lot/Eqp/时间用 Mono；状态三重编码
- 主路径按钮动词明确：Track In、Hold Lot、Release

### Don't
- 不要玻璃拟态卡片墙、渐变字、emoji 图标
- 不要每屏不同按钮圆角或主色
- 不要用 Modal 堆长表单
- 不要让 KPI 大色块抢过操作区
- 不要忽略 reduced-motion 与触控热区
