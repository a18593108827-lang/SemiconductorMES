---
type: plan
module: 
status: approved
slices: [EVAL-0001]
aligns: [EVAL-0001-追溯包抽屉并发下载重入.md, Doc-4-plan.md]
updated: 2026-09-23
---
# EVAL-0001 计划 — 前端交互行为用例（vitest + Testing Library + CI）

> 对齐：`docs/eval/EVAL-0001-追溯包抽屉并发下载重入.md`（回归校验段落）· `docs/架构/Doc-4-plan.md`（后端回归网已落地）
> 批准依据：2026-09-23 用户选定方案 A（「A 吧」：完整行为用例 + CI 接入）
> 前置事实：`web` 目前只有 `tsc -b` 类型门禁，**无任何测试框架**（无 vitest/jest、无 @testing-library）；npm registry 直连可达（已实测）

## 1. 目标与边界

**一句话**：把 EVAL-0001 的回归校验从「人工连点 + 眼睛 grep」变成 `npm test` 里**自动会红**的用例。

**做**

- 引入 `vitest` + `jsdom` + `@testing-library/react` + `@testing-library/user-event` + `@testing-library/jest-dom`（**全部 devDependencies**）
- `web/vitest.config.ts` + `web/src/test/setup.ts`；`package.json` 增 `test` 脚本
- `ComplaintPackageDrawer.test.tsx` —— 2 条行为用例，精确复刻 EVAL-0001 的触发序列：
  1. **连点双钮只发 1 个请求**：在途时两钮同时 disabled，且 `finally` 收尾后恢复
  2. **会话重开不被旧请求污染**：下载在途 → 换 `anchorLotId` 重开 → 重新生成 → 新下载在途时 resolve **旧**请求，新会话 loading 不得被清掉（这条直接锁 `downloadSeq`）
- CI `web` job 增 `npm test` 步骤

**不做（负面清单）**

- ❌ 不做全量组件测试 / 不追求覆盖面，只覆盖本缺陷的触发序列
- ❌ 不引入 Playwright / 真实浏览器（jsdom 足够；真机路径已由 CP-6 的 HTTP 验收覆盖）
- ❌ 不改弹窗 / 抽屉 / 按钮等 UI 组件的实现
- ❌ 不动后端

## 2. 约束

| # | 约束 | 说明 |
|---|------|------|
| K1 | 测试不触网、不依赖后端 | mock `api/complaint`、`api/hold` 与 UI 依赖（Toast / Confirm / Drawer） |
| K2 | 生产行为零改动 | 只加测试与配置。若发现必须改才可测，先改 plan 再动码 |
| K3 | 用例必须能失败 | 反向验证：临时摘掉 `downloadingRef` 闸门 → 用例 1 必须变红；临时摘掉 `downloadSeq` 守卫 → 用例 2 必须变红 |
| K4 | 只引 devDependencies | `dependencies` 不得新增（运行期包体积零变化） |
| K5 | CI 步骤可失败 | `npm test` 用 `vitest run`（非 watch），失败即红 |

## 3. 实施步骤

a. `web` 安装 devDeps（vitest / jsdom / @testing-library/react / user-event / jest-dom）
b. `web/vitest.config.ts`：`environment: 'jsdom'`、`setupFiles: ['./src/test/setup.ts']`、`include: ['src/**/*.test.{ts,tsx}']`、`plugins: [react()]`
c. `web/src/test/setup.ts`：注册 jest-dom 匹配器
d. `package.json` 增 `"test": "vitest run"`
e. `ComplaintPackageDrawer.test.tsx`：mock 依赖 → 驱动「预览 → 生成 → built」→ 跑两条断言
f. `.github/workflows/ci.yml` 的 web job 增 `npm test`
g. 文档同步：EVAL-0001「回归校验」段落改为「已落成用例 + 命令」，INDEX 重建

## 4. 验收（可判真假）

1. `npm test` 在 `web/` 下全绿；用例数 ≥ 2
2. **反向验证两条**（K3）：分别摘掉 `downloadingRef` 早退与 `downloadSeq` 比较，对应用例必须变红，随后还原
3. `npx tsc -b` 仍通过（测试文件与配置纳入类型检查不报错）
4. `git diff` 范围：`web/package.json` + `web/package-lock.json` + 新增测试/config + CI + 文档；**生产代码零改动**（K2）
5. `web/package.json` 的 `dependencies` 未变（K4）

## 5. 回滚

删 `web/vitest.config.ts`、`web/src/test/`、测试文件、CI 的 `npm test` 步骤，并 `npm uninstall` 五个 devDeps 即可。无运行期影响。

## 6. 遗留（本切片不做）

| 项 | 现状 | 触发条件 | 方案 |
|----|------|----------|------|
| 其它前端交互用例 | 无 | 封测主线前端开工时 | 按同样模式补（多入口 / 并发 / 重开） |
| 前端覆盖率门槛 | 无 | 团队扩大 | 引入 vitest coverage + 阈值 |
| 组件视觉 / 端到端 | 无 | 需要真实浏览器验证 | Playwright，另开切片 |

---

## 实施记录（2026-09-23 完成）

**变更文件**

| 文件 | 变更 |
|------|------|
| `web/package.json` | + devDeps：`vitest@^5.0.1`、`jsdom@^30.1.1`、`@testing-library/react@^16.3.3`、`@testing-library/user-event@^14.6.7`、`@testing-library/jest-dom@^7.0.1`；+ `"test": "vitest run"` |
| `web/vitest.config.ts`（新增） | jsdom 环境 + setup 文件 + `include: src/**/*.test.{ts,tsx}` + react 插件 |
| `web/src/test/setup.ts`（新增） | 注册 jest-dom 匹配器 + **显式 `afterEach(cleanup)`**（见 F3） |
| `web/src/components/lot/ComplaintPackageDrawer.test.tsx`（新增） | 2 条行为用例（mock api/hold/Toast/Confirm/Drawer，驱动「预览 → 生成 → built → 下载」） |
| `.github/workflows/ci.yml` | `web` job 增 `npm test（前端交互行为用例）` 步骤 |
| 文档 | 本 plan · `docs/eval/EVAL-0001-*.md`（回归校验段落改为自动化优先 + 反向验证证据）· INDEX 重建 |

**验收结果（逐条对应 §4）**

| # | 结论 |
|---|------|
| 1 | ✅ `cd web && npm test` → **Test Files 1 passed，Tests 2 passed**；`npx tsc -b` 仍 EXIT=0 |
| 2 | ✅ **反向验证两条都命中**：摘 `downloadingRef` 早退 → 用例 1 报 `expected to be called 1 times, but got 2 times`；摘 `downloadSeq` 比较 → 用例 2 报 `Unable to find ... "处理中…"`（新会话 loading 被旧请求清掉）。两次均已还原 |
| 3 | ✅ 测试文件纳入 `tsc -b` 无报错 |
| 4 | ✅ `git diff` 范围：`web/package.json` + `web/package-lock.json` + 新增测试/config + CI + 文档；**生产代码零改动**（`git diff web/src/components/lot/ComplaintPackageDrawer.tsx` 为空） |
| 5 | ✅ `dependencies` 未变（只加 devDependencies）；`react` / `vite` 版本未动 |

**实施期事实（F#）**

| # | 事实 | 影响 |
|---|------|------|
| F1 | **只靠「点两下」测不到 ref 闸门**：第一版用例用 `user.click` 连点两钮，摘掉 `downloadingRef` 后**仍全绿**——因为 `disabled={... \|\| downloading != null}` 已经把第二钮锁住，user-event 尊重 disabled 不派发点击 | 真正的竞态窗口是「React 提交之前连点」。改用同一 `act()` 内 `dispatchEvent(new MouseEvent('click'))` 连发两次：此时 DOM 的 `disabled` 尚未更新，只有同步 ref 能挡 → 反向验证才变红 |
| F2 | `Button` 在 `loading` 时会用「处理中…」**替换 children** → 该钮的可访问名变了 | 在途断言要按 `name: '处理中…'` 找（顺带成了「新会话是否仍在下载中」的探针）；未在途的钮仍保留原文案 |
| F3 | vitest 未开 `globals` 时 **RTL 不会自动 cleanup** | 第二个用例报 `Found multiple elements ... 下载 ZIP`（上例 DOM 残留）→ 在 setup 里显式 `afterEach(() => cleanup())` |
| F4 | 抽屉依赖 `toast` / `confirm` / `Drawer`（GSAP 动画 + portal） | 与本缺陷无关的 UI 依赖一律 mock 成最小容器，让测试只盯「重入闸 + 会话归属」这两件事 |
