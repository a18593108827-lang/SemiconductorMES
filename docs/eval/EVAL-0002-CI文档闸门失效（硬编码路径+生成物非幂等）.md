---
type: eval
module: 
status: active
slices: []
aligns: [Doc-4-plan.md]
updated: 2026-09-24
---
# EVAL-0002 CI 文档闸门失效（硬编码路径 + 生成物非幂等）

> 来源：用户推送后于 GitHub Actions 发现 `docs` job 失败（2026-09-24 发现）· 归属：CI / 文档工具链（`.workbuddy/scripts/add_frontmatter.py` + `.github/workflows/ci.yml`）· 治理规则 G5
> 性质：**工具链 / 门禁缺陷**（非产品功能缺陷）—— 该闸门自 Doc-4 落地（2026-09-23）起**从未真正跑通过**，即「frontmatter + 状态列」这道门禁在事实上一直不存在
> 修复提交：`e6220bf`
> 本文件是 `docs/eval/` 启用后的第 2 条

## 触发条件

- 只要 CI 在 Linux runner 上执行 `.github/workflows/ci.yml` 的 `docs` job（步骤 `reindex 后 INDEX 必须无差异`）
- **任何** push 都会命中 —— 与代码内容无关、与文档内容也无关

## 复现步骤

1. push 任意提交到 `main`（如 `0fffa61` / `9fdf6c8`）
2. GitHub Actions → `ci` → `docs` job → 步骤 `reindex 后 INDEX 必须无差异`
3. 观察失败（`0fffa61` 的失败日志）：

```
Traceback (most recent call last):
  File ".../add_frontmatter.py", line 214, in main
    build_index(items)
  File ".../add_frontmatter.py", line 209, in build_index
    with open(os.path.join(DOCS, "INDEX.md"), "w") as f:
FileNotFoundError: [Errno 2] No such file or directory:
  'D:\java\xm\2026_07\MES\docs\INDEX.md'
Error: Process completed with exit code 1.
```

4. 对照：本地跑**完全相同**的 `python .workbuddy/scripts/add_frontmatter.py --reindex` 永远成功 —— 因为本地恰好就是那台 Windows 机器

## 预期正确行为

- 索引脚本与生成物**与执行环境无关**：本地 Windows 与 CI Linux 产出逐字节相同的 `docs/INDEX.md`
- 门禁在「文档与 INDEX 不一致」「frontmatter 被破坏（状态列回落）」时**真的变红** —— 这才是它存在的意义（Doc-5 把它当作 frontmatter 状态校验的门禁）

## 根因（两个，互相独立）

### R1 硬编码绝对路径（直接原因）

`add_frontmatter.py:6`：

```python
DOCS = r"D:\java\xm\2026_07\MES\docs"
```

CI 跑在 `ubuntu-latest`，没有 `D:` 盘。更隐蔽的一点：`os.walk(DOCS)` 对**不存在的目录不报错**（静默返回空列表）→ 异常直到最后 `open(os.path.join(DOCS, "INDEX.md"), "w")` 才抛出，报错位置离病因很远（line 209 vs line 6）。

### R2 生成物含 `date.today()`，非幂等（更隐蔽，修完 R1 才会撞上）

`build_index()` 用 `datetime.date.today()` 写两处：frontmatter 的 `updated:` 与正文的 `> 生成：…`。

于是：同一天跑一致，**次日再跑必然产出不同 INDEX** → `git diff --exit-code` 失败。也就是说即便 R1 修好，只要 CI 不是在「INDEX 最后生成的那一天」执行，闸门依然会红 —— R2 让这道门禁在**跨天场景下必然误报**。

> 这也是为什么必须**两个都修**：只修 R1，闸门会从「永远报路径错」变成「隔天误报差异」，仍然不可用。

## 修法（已落地）

| 手段 | 内容 |
|------|------|
| 路径改为**相对脚本位置推断** | `_HERE = os.path.dirname(os.path.abspath(__file__))` → `_REPO` = 上两级 → `DOCS = os.path.join(_REPO, "docs")`；本地与 CI 行为一致 |
| 日期改为**内容派生** | `stamps = [e["updated"] for e in entries if e["updated"]]`；`today = max(stamps) if stamps else datetime.date.today().isoformat()` → 输出只随文档内容变化，不随系统时钟 |

## 回归校验

**已执行（可复现）**

| # | 验证 | 结果 |
|---|------|------|
| 1 | 本地行为未变 | 修复前后 `md5sum docs/INDEX.md` **一致**（`c33edeb95d0f5643d164f3cd1c3d012b`）；`git status` 无输出 |
| 2 | **模拟 runner 目录结构** | 脚本 + 2 篇文档复制到陌生路径（`.workbuddy/tmp/ci-sim/fake-repo/`）执行 → 正确产出 `docs/INDEX.md`（2 篇）→ 证明不再依赖硬编码路径 |
| 3 | **跨天幂等对照实验** | 把副本文档 `updated` 改为 `2026-08-15` → INDEX 的 `updated:` 与 `> 生成：` **均变为 `2026-08-15`**（当时系统日期为 `2026-09-24`）→ 证明日期跟随内容而非时钟 |

**未执行（本机无法执行，待推送后确认）**

- `docs` job 转绿：GitHub Actions 只能在推送后由平台执行，**不得据「本地通过」宣称已修好**。补做条件：推送 → 看 Actions 的 `docs` job → 步骤 `reindex 后 INDEX 必须无差异` 通过。

**代码断言（可 grep）**

- `add_frontmatter.py` 中**零** `D:\` 字面量；`DOCS` 由 `__file__` 推出
- INDEX 的日期不来自 `date.today()`（`date.today()` 仅作「文档内容无 updated 时」的回落分支）

## 防复发（已回流）

| 回流点 | 内容 |
|--------|------|
| **CI 约定（新增，本条核心）** | ① **凡被 CI 调用的脚本**禁止硬编码绝对路径（必须相对脚本位置 / 仓库根推断）② **凡被 `git diff --exit-code` 比对的生成物**禁止含 `date.today()` 等时钟输入 —— 「同一天跑得通」不等于幂等 ③ 新增 / 修改 CI 步骤后，**必须在真实 runner 上验证一次**，本地通过不能作为依据 |
| 通用验证套路 | 本条沉淀两个不依赖真 CI 的验证手法：**模拟 runner 目录结构**（换路径跑，破硬编码）+ **跨天对照实验**（改内容日期看输出，破时钟依赖）—— 适用于一切「生成物 + diff 门禁」类缺陷 |
| `docs/eval/README.md` | 落档口径新增一档「**CI / 工具链自身缺陷**」：此前只写了「生产事故」与「验收期缺陷」，未覆盖「门禁 / 脚本自身失效」这一类 |
| `docs/架构/Doc-4-plan.md` | 视为 Doc-4 的落实缺陷（回归网 + CI 落地时未在真实 runner 验证），其「CI 三闸门」表述需补一句「已在真 runner 验证通过」才算真正闭环 |

## 关联

- `docs/架构/Doc-4-plan.md` — 回归网与 CI 三闸门落地（本闸门的来源）
- `.github/workflows/ci.yml` — `docs` job（`reindex 后 INDEX 必须无差异`）
- `.workbuddy/scripts/add_frontmatter.py` — 索引脚本（已修）
- `docs/架构/MES-AI原生SDLC文档体系重构方案.md` — Doc-5（frontmatter 状态校验门禁）
