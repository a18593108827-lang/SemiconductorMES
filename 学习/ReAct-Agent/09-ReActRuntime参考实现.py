# -*- coding: utf-8 -*-
"""
ReAct Runtime 参考实现（带护栏）
================================

这是 05-工程治理 那份伪代码的**可运行版本**：用 Mock LLM + Mock 工具，把
「死循环 / 工具滥用 / 成本失控」三类护栏真实跑一遍，输出可读的 Trace 日志。

运行：
    python 09-ReActRuntime参考实现.py

无任何第三方依赖，纯标准库，确定性输出（不用随机数），便于反复对照阅读。

与 05 文档里原始伪代码的差异（落地时修正的三处）
--------------------------------------------------
1. **动作去重存在 off-by-one**。原文写 `if count >= 2: inject_hint; continue`，
   但 `continue` 会跳过 append，计数器永远停在 2 → 变成无限注入提示的死循环。
   本实现改为「先自增计数，再判 3 次终止 / 2 次注入提示」。
2. **熔断点位置**。原文把熔断判断放在工具执行之后，本实现放在执行之前，
   否则「已熔断」的工具仍会被调用一次才被拦下。
3. **无进展提示只注入一次**（`no_progress == 2` 而非 `>= 2`），避免提示在
   上下文里重复堆积——提示本身也是 token 成本。
"""

from __future__ import annotations

import difflib
import hashlib
import json
import sys
from dataclasses import dataclass, field
from typing import Any, Callable

# Windows 控制台默认可能是 cp936，打印 emoji / 生僻字会抛 UnicodeEncodeError
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

# ============================================================================
# 0. Trace：每一步都要可回放（观测层）
# ============================================================================


class Trace:
    """极简追迹器：真实系统里这里应是 OpenTelemetry Span + 上报。"""

    def __init__(self) -> None:
        self.events: list[dict[str, Any]] = []

    def log(self, step: int, kind: str, **payload: Any) -> None:
        self.events.append({"step": step, "kind": kind, **payload})

    def dump(self, indent: int = 4) -> None:
        pad = " " * indent
        for e in self.events:
            kind = e.pop("kind")
            step = e.pop("step")
            body = "  ".join(f"{k}={v!r}" for k, v in e.items())
            print(f"{pad}[step {step}] {kind:<12} {body}")


# ============================================================================
# 1. 工具层：Tool Registry
# ============================================================================


class ToolError(Exception):
    """工具执行失败。会被 Runtime 转成 Observation 回填给模型。"""


@dataclass
class ToolSpec:
    name: str
    description: str
    fn: Callable[..., Any]
    cost: float = 0.0            # 每次调用成本（美元）—— 用于成本核算
    high_risk: bool = False      # 高风险动作：退款/删除/回滚/发邮件/执行 SQL
    # 参数规则：{参数名: {"type": type, "required": bool, "min": n, "max": n}}
    params: dict[str, dict[str, Any]] = field(default_factory=dict)
    fail_times: int = 0          # 连续失败次数（用于熔断）
    disabled_until_step: int = -1


def build_tools() -> dict[str, ToolSpec]:
    """构造一组 Mock 工具。返回结构模拟真实的订单客服场景。"""

    def get_order(order_id: str) -> dict:
        return {"status": "shipped", "carrier": "SF", "tracking_no": "SF123"}

    def get_tracking(tracking_no: str) -> dict:
        return {"status": "delayed", "eta": "+2天"}

    def retrieve_kb(query: str) -> str:
        return "延误超过24小时补偿10元"

    def search_kb(query: str) -> str:
        # 故意做成「换关键词但返回同样内容」，用来演示「无进展检测」
        return "延误超过24小时补偿10元"

    def search_web(query: str) -> dict:
        return {"results": ["..."]}

    def apply_compensation(order_id: str, amount: float) -> dict:
        return {"success": True, "amount": amount}

    def escalate_to_human(reason: str) -> dict:
        return {"ticket": "T-20260924-001", "reason": reason}

    def run_sql(sql: str) -> dict:
        # 故意永远失败，用来演示「工具失败熔断」
        raise ToolError("SQL execution error: table not found")

    return {
        t.name: t
        for t in [
            ToolSpec(
                "get_order", "根据订单号查询订单状态。仅当用户提供订单号时使用。不要用于查物流。",
                get_order, cost=0.001,
                params={"order_id": {"type": str, "required": True}},
            ),
            ToolSpec(
                "get_tracking", "根据物流单号查询物流轨迹。",
                get_tracking, cost=0.001,
                params={"tracking_no": {"type": str, "required": True}},
            ),
            ToolSpec(
                "retrieve_kb", "检索内部知识库，用于查询政策、规则、条款。",
                retrieve_kb, cost=0.0005,
                params={"query": {"type": str, "required": True}},
            ),
            ToolSpec(
                "search_kb", "全文检索知识库（与 retrieve_kb 功能重叠，用于演示选错工具）。",
                search_kb, cost=0.0005,
                params={"query": {"type": str, "required": True}},
            ),
            ToolSpec(
                "search_web", "搜索网页。每次调用成本较高，仅在知识库无结果时使用。",
                search_web, cost=0.02,
                params={"query": {"type": str, "required": True}},
            ),
            ToolSpec(
                "apply_compensation", "【高风险】为用户申请补偿，涉及真实资金支出。",
                apply_compensation, cost=0.0, high_risk=True,
                params={
                    "order_id": {"type": str, "required": True},
                    "amount": {"type": (int, float), "required": True, "min": 0, "max": 1000},
                },
            ),
            ToolSpec(
                "escalate_to_human", "转人工客服。当自动流程无法完成或需要人工确认时使用。",
                escalate_to_human, cost=0.0,
                params={"reason": {"type": str, "required": True}},
            ),
            ToolSpec(
                "run_sql", "执行只读 SQL 查询（演示用，必定失败）。",
                run_sql, cost=0.0,
                params={"sql": {"type": str, "required": True}},
            ),
        ]
    }


# ============================================================================
# 2. 治理层：Tool Gateway + Policy Engine
# ============================================================================


class ToolGateway:
    """所有工具调用的唯一入口：鉴权 → 校验 → 限流 → 缓存 → 熔断 → 执行 → 审计。

    生产环境对应：OpenAPI 网关 / Lambda 调用层 / RPC 客户端封装。
    """

    def __init__(self, tools: dict[str, ToolSpec], trace: Trace, *, user_confirmed: bool = False):
        self.tools = tools
        self.trace = trace
        self.user_confirmed = user_confirmed     # 本任务是否已通过 HITL 确认
        self.cache: dict[str, Any] = {}          # 演示用进程内缓存
        self.call_count = 0
        self.audit: list[dict[str, Any]] = []
        self.cache_hits = 0

    # ---- 参数校验（JSON Schema 简化版）----
    @staticmethod
    def _validate(spec: ToolSpec, args: dict[str, Any]) -> str | None:
        for pname, rule in spec.params.items():
            if rule.get("required") and pname not in args:
                return f"参数缺失：{pname}"
            if pname not in args:
                continue
            val = args[pname]
            if "type" in rule and not isinstance(val, rule["type"]):
                return f"参数类型错误：{pname} 应为 {rule['type']}"
            if rule.get("min") is not None and val < rule["min"]:
                return f"参数越界：{pname}={val} 小于最小值 {rule['min']}"
            if rule.get("max") is not None and val > rule["max"]:
                return f"参数越界：{pname}={val} 大于最大值 {rule['max']}"
        return None

    @staticmethod
    def _cache_key(action: str, args: dict[str, Any]) -> str:
        return f"{action}:{hashlib.md5(json.dumps(args, sort_keys=True, ensure_ascii=False).encode()).hexdigest()[:8]}"

    def execute(self, action: str, args: dict[str, Any], *, step: int) -> str:
        # ① 工具存在性
        spec = self.tools.get(action)
        if spec is None:
            return f"拒绝：不存在名为 {action} 的工具"

        # ② 熔断：连续失败 2 次 → 禁用一段时间
        if spec.fail_times >= 2:
            self.trace.log(step, "circuit_break", tool=action)
            return f"该工具暂时不可用（{action} 连续失败 {spec.fail_times} 次），请换其他方法"

        # ③ 参数校验
        err = self._validate(spec, args)
        if err:
            return f"拒绝：{err}"

        # ④ 高风险动作 → HITL（人工确认）
        if spec.high_risk and not self.user_confirmed:
            self.trace.log(step, "hitl_block", tool=action, args=args)
            return f"拒绝：{action} 属于高风险动作，需人工确认后才能执行"

        # ⑤ 缓存
        key = self._cache_key(action, args)
        if key in self.cache:
            self.cache_hits += 1
            self.trace.log(step, "cache_hit", tool=action)
            return self.cache[key]

        # ⑥ 执行
        try:
            result = spec.fn(**args)
        except ToolError as e:
            spec.fail_times += 1
            self.trace.log(step, "tool_error", tool=action, fail_times=spec.fail_times)
            return f"工具执行失败：{e}"
        except Exception as e:  # noqa: BLE001
            spec.fail_times += 1
            return f"工具异常：{e}"

        spec.fail_times = 0
        self.call_count += 1
        observation = json.dumps(result, ensure_ascii=False) if isinstance(result, (dict, list)) else str(result)
        self.cache[key] = observation
        self.audit.append({"step": step, "action": action, "args": args, "cost": spec.cost})
        self.trace.log(step, "tool_ok", tool=action, cost=spec.cost)
        return observation


# ============================================================================
# 3. Mock 模型层：用脚本代替真实 LLM
# ============================================================================


class MockLLM:
    """按剧本输出决策，用来稳定复现各类护栏。

    真实实现里 decide() 内部是：llm(history, tools=tool_schemas)，
    返回结构化 Tool Call 或 Final Answer。
    """

    def __init__(self, script: list[dict[str, Any]]):
        self.script = script
        self.cursor = 0

    def decide(self, history: list[dict[str, Any]]) -> dict[str, Any]:
        idx = min(self.cursor, len(self.script) - 1)
        self.cursor += 1
        return json.loads(json.dumps(self.script[idx]))  # 深拷贝，避免污染剧本


# ============================================================================
# 4. 编排层：带护栏的 ReAct Runtime
# ============================================================================


class Fallback(Exception):
    """内部信号：护栏强制终止。"""

    def __init__(self, reason: str):
        self.reason = reason


def _extract_state(observation: str) -> str:
    """抽取观察里的「关键状态」——用于无进展检测。生产里应是结构化字段选集。"""
    return observation.strip()


def _similar(a: str | None, b: str | None) -> float:
    if a is None or b is None:
        return 0.0
    return difflib.SequenceMatcher(None, a, b).ratio()


class ReActRuntime:
    """模型只负责决策，刹车全在这里。"""

    def __init__(
        self,
        user: str,
        task: str,
        *,
        llm: MockLLM,
        gateway: ToolGateway,
        trace: Trace,
        max_steps: int = 8,
        max_tool_calls: int = 10,
        max_tokens: int = 8000,
    ):
        self.user, self.task = user, task
        self.llm, self.gateway, self.trace = llm, gateway, trace
        # 预算（硬约束）
        self.max_steps, self.max_tool_calls, self.max_tokens = max_steps, max_tool_calls, max_tokens
        self.tokens_used = 0
        self.cost = 0.0
        # 状态
        self.action_counts: dict[str, int] = {}
        self.state_history: list[str] = []
        self.no_progress = 0
        self.history: list[dict[str, Any]] = [{"thought": None, "action": None, "observation": task}]
        self.hints: list[str] = []

    # -- 护栏 1：动作哈希去重 ------------------------------------------------
    @staticmethod
    def _action_key(action: str, args: dict[str, Any]) -> str:
        return action + "|" + json.dumps(args, sort_keys=True, ensure_ascii=False)

    # -- 护栏 2：无进展检测 --------------------------------------------------
    def _check_no_progress(self, observation: str, step: int) -> None:
        state = _extract_state(observation)
        prev = self.state_history[-1] if self.state_history else None
        self.no_progress = self.no_progress + 1 if _similar(state, prev) > 0.95 else 0
        self.state_history.append(state)

        if self.no_progress == 2:
            self.hints.append("连续两步没有新信息，请重新规划或直接给出最终答案")
            self.trace.log(step, "no_progress", count=self.no_progress)
        if self.no_progress >= 3:
            raise Fallback("无进展（连续 3 步获得相同状态）")

    def run(self) -> str:
        for step in range(1, self.max_steps + 1):
            # ── 预算闸门 ──────────────────────────────────────────────
            if self.tokens_used > self.max_tokens:
                return self._fallback("token 预算耗尽")
            if self.gateway.call_count > self.max_tool_calls:
                return self._fallback("工具调用次数超限")

            # ── 模型决策 ──────────────────────────────────────────────
            decision = self.llm.decide(self.history)
            self.tokens_used += decision.get("usage", 0) + 120   # 模拟每次决策的 token 消耗

            if decision.get("type") == "final":
                self.trace.log(step, "final", content=decision["content"])
                print(f"    ✅ Final: {decision['content']}")
                return decision["content"]

            thought, action, args = decision["thought"], decision["action"], decision["args"]

            # ── 护栏 1：动作哈希去重 ──────────────────────────────────
            key = self._action_key(action, args)
            self.action_counts[key] = self.action_counts.get(key, 0) + 1
            n = self.action_counts[key]
            if n >= 3:
                self.trace.log(step, "dedup_terminate", action=action, times=n)
                return self._fallback(f"重复动作过多（同一动作已执行 {n} 次）")
            if n == 2:
                self.trace.log(step, "dedup_hint", action=action)
                self.hints.append(f"你已执行过 {action}({args})，请换策略或直接回答")
                continue

            print(f"    Thought: {thought}")
            print(f"    Action : {action}({args})")

            # ── 治理层：策略 + 工具网关 ───────────────────────────────
            try:
                observation = self.gateway.execute(action, args, step=step)
                self.cost += self.gateway.tools[action].cost if action in self.gateway.tools else 0.0
                print(f"    Observ.: {observation}")

                # ── 护栏 2：无进展检测 ────────────────────────────────
                self._check_no_progress(observation, step)
            except Fallback as f:
                return self._fallback(f.reason)

            self.history.append({"thought": thought, "action": action, "observation": observation})

        return self._fallback("达到最大步数")

    def _fallback(self, reason: str) -> str:
        msg = f"已中止：{reason}，转人工处理"
        self.trace.log(self.max_steps + 1, "fallback", reason=reason)
        print(f"    🛑 Fallback: {msg}")
        return msg

    def stats(self) -> str:
        return (
            f"步数预算 {self.max_steps} | token 消耗 {self.tokens_used} | "
            f"成功工具调用 {self.gateway.call_count} 次 | 缓存命中 {self.gateway.cache_hits} 次 | "
            f"成本 ${self.cost:.4f}"
        )


# ============================================================================
# 5. 场景演示
# ============================================================================


def demo(title: str, script: list[dict[str, Any]], *, user_confirmed: bool = False) -> None:
    print("=" * 78)
    print(f"场景：{title}")
    print("=" * 78)
    tools = build_tools()
    trace = Trace()
    gateway = ToolGateway(tools, trace, user_confirmed=user_confirmed)
    runtime = ReActRuntime(
        user="u_1001", task="用户反馈订单 123 没收到货", llm=MockLLM(script),
        gateway=gateway, trace=trace,
    )
    runtime.run()
    print(f"    📊 {runtime.stats()}")
    if runtime.hints:
        print(f"    💡 注入过的软控制提示：{runtime.hints}")
    print("    🔍 Trace（生产环境应上报 OpenTelemetry）：")
    trace.dump(8)
    print()


def main() -> None:
    # ── 场景 1：正常收敛（Happy Path）────────────────────────────────
    demo("正常收敛：查订单 → 查物流 → 查政策 → 给答案", [
        {"thought": "用户说没收到货，先查订单状态", "action": "get_order", "args": {"order_id": "123"}},
        {"thought": "订单已发货，需要查物流", "action": "get_tracking", "args": {"tracking_no": "SF123"}},
        {"thought": "物流延误，查补偿政策", "action": "retrieve_kb", "args": {"query": "延误补偿政策"}},
        {"type": "final", "content": "您的订单因物流延误，可申请 10 元补偿。"},
    ])

    # ── 场景 2：重复动作 → 硬刹车 ───────────────────────────────────
    demo("死循环 · 重复动作：反复查同一订单", [
        {"thought": "查订单状态", "action": "get_order", "args": {"order_id": "123"}},
        {"thought": "再确认一下订单", "action": "get_order", "args": {"order_id": "123"}},
        {"thought": "还是想再看一次订单", "action": "get_order", "args": {"order_id": "123"}},
    ])

    # ── 场景 3：无进展 → 状态哈希刹车 ────────────────────────────────
    demo("死循环 · 无进展：换关键词但结果一模一样", [
        {"thought": "搜物流延误规则", "action": "search_kb", "args": {"query": "物流延误"}},
        {"thought": "换个词搜补偿规则", "action": "search_kb", "args": {"query": "延误补偿"}},
        {"thought": "再换个词搜补偿标准", "action": "search_kb", "args": {"query": "补偿标准"}},
        {"thought": "继续搜赔付细则", "action": "search_kb", "args": {"query": "赔付细则"}},
    ])

    # ── 场景 4：高风险动作 → HITL 拦截 → 模型改走转人工 ───────────────
    demo("工具滥用 · 高风险动作：未确认直接申请补偿", [
        {"thought": "物流延误，直接申请补偿", "action": "apply_compensation",
         "args": {"order_id": "123", "amount": 10}},
        {"thought": "被拒绝，需要转人工确认", "action": "escalate_to_human",
         "args": {"reason": "补偿申请需人工确认"}},
        {"type": "final", "content": "已为您转接人工客服处理补偿申请，工单号 T-20260924-001。"},
    ])

    # ── 场景 5：参数越界 → 网关校验拦截 ──────────────────────────────
    demo("工具滥用 · 参数越界：金额超出策略上限", [
        {"thought": "多赔一点用户会开心", "action": "apply_compensation",
         "args": {"order_id": "123", "amount": 99999}},
        {"type": "final", "content": "补偿金额超出可申请范围，已转人工评估。"},
    ], user_confirmed=True)   # 即使已人工确认，参数校验仍会拦下

    # ── 场景 6：工具失败 → 熔断 ─────────────────────────────────────
    demo("工具失败熔断：同一工具连续失败后禁用", [
        {"thought": "先查数据库看看", "action": "run_sql", "args": {"sql": "select * from orders"}},
        {"thought": "重试一次", "action": "run_sql", "args": {"sql": "select 1"}},
        {"thought": "再试一次", "action": "run_sql", "args": {"sql": "select 2"}},
        {"type": "final", "content": "数据查询暂时不可用，已转人工。"},
    ])

    # ── 场景 7：缓存命中（成本控制）──────────────────────────────────
    print("=" * 78)
    print("场景：缓存命中 —— 相同工具+相同参数不重复付钱")
    print("=" * 78)
    tools = build_tools()
    trace = Trace()
    gateway = ToolGateway(tools, trace)
    a = gateway.execute("get_order", {"order_id": "123"}, step=1)
    b = gateway.execute("get_order", {"order_id": "123"}, step=2)
    print(f"    第一次返回：{a}")
    print(f"    第二次返回：{b}")
    print(f"    📊 实际执行次数 {gateway.call_count} 次，缓存命中 {gateway.cache_hits} 次")
    print()


if __name__ == "__main__":
    main()
