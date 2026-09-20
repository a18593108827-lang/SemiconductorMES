import os
import re
import sys
import datetime

DOCS = r"D:\java\xm\2026_07\MES\docs"
SKIP_DIRS = {"_templates"}
SKIP_FILES = {"README.md", "INDEX.md"}
BOM = b"\xef\xbb\xbf"

TYPE_ORDER = ["intent", "方案", "架构", "功能文档", "数据库设计", "接口设计",
              "功能清单", "已完成功能", "plan", "eval", "进度",
              "业务清单", "UI", "WI"]


def detect_type(relpath, fname):
    parts = relpath.split(os.sep)
    top = parts[0] if len(parts) > 1 else ""
    if top == "方案":
        return "方案"
    if top == "业务清单":
        return "业务清单"
    if top == "UI":
        return "UI"
    if top == "架构":
        if "实施进度" in fname:
            return "进度"
        return "架构"
    if len(parts) >= 3 and parts[0] == "模块":
        if "数据库设计" in fname:
            return "数据库设计"
        if "接口设计" in fname:
            return "接口设计"
        if "已完成功能" in fname:
            return "已完成功能"
        if "功能文档" in fname:
            return "功能文档"
        if "功能清单" in fname:
            return "功能清单"
        if fname.startswith("WI-"):
            return "WI"
        return "架构"
    return "架构"


def detect_module(relpath):
    parts = relpath.split(os.sep)
    if len(parts) >= 3 and parts[0] == "模块":
        d = parts[1]
        m = re.match(r"^([A-Za-z]+)（", d)
        if m:
            return m.group(1)
        if "权限" in d:
            return "权限用户"
        return d
    return ""


def detect_status(head, dtype, fname):
    if dtype in ("已完成功能", "进度", "INDEX"):
        return "done"
    if dtype == "方案":
        return "draft"
    if "架构规划" in fname:
        return "draft"
    m = re.search(r"状态[：:]\s*(.+)", head)
    if m:
        s = m.group(1)
        if any(k in s for k in ("✅", "已完成", "已落地", "闭环")):
            return "done"
        if any(k in s for k in ("后置", "未", "规划", "待", "draft", "Draft")):
            return "in-progress"
        return "done"
    return "done"


def detect_updated(head):
    m = re.search(r"更新[：:]\s*(\d{4}-\d{2}-\d{2})", head)
    return m.group(1) if m else ""


def detect_title(text):
    for line in text.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return ""


def scan():
    results = []
    for root, dirs, files in os.walk(DOCS):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith(".")]
        for fn in files:
            if not fn.endswith(".md") or fn in SKIP_FILES:
                continue
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, DOCS)
            raw = open(full, "rb").read()
            body = raw[len(BOM):] if raw.startswith(BOM) else raw
            has_fm = body.startswith(b"---")
            text = body.decode("utf-8", errors="replace")
            head = "\n".join(text.splitlines()[:20])
            results.append({
                "full": full, "rel": rel, "raw": raw, "body": body, "has_fm": has_fm,
                "type": detect_type(rel, fn), "module": detect_module(rel),
                "status": detect_status(head, detect_type(rel, fn), fn),
                "updated": detect_updated(head), "title": detect_title(text),
            })
    return results


def add_frontmatter(items):
    changed, skipped = [], []
    for it in items:
        if it["has_fm"]:
            skipped.append(it)
            continue
        eol = b"\r\n" if b"\r\n" in it["body"][:500] else b"\n"
        fm_lines = [
            "---",
            "type: %s" % it["type"],
            "module: %s" % it["module"],
            "status: %s" % it["status"],
            "slices: []",
            "aligns: []",
            "updated: %s" % it["updated"],
            "---",
        ]
        fm = eol.join(l.encode("utf-8") for l in fm_lines) + eol + eol
        out = (BOM if it["raw"].startswith(BOM) else b"") + fm + it["body"]
        with open(it["full"], "wb") as f:
            f.write(out)
        changed.append(it)
    return changed, skipped


def parse_fm(text):
    if not text.startswith("---"):
        return {}
    lines = text.splitlines()
    out = {}
    for l in lines[1:]:
        if l.strip() == "---":
            break
        m = re.match(r"^(\w+)[：: ]\s*(.*)$", l)
        if m:
            out[m.group(1)] = m.group(2).strip()
    return out


def build_index(items):
    today = datetime.date.today().isoformat()
    entries = []
    for it in items:
        text = open(it["full"], "rb").read().decode("utf-8", errors="replace")
        fm = parse_fm(text)
        entries.append({
            "rel": it["rel"].replace(os.sep, "/"),
            "title": it["title"] or os.path.basename(it["rel"]),
            "type": fm.get("type", it["type"]),
            "module": fm.get("module", "") or it["module"],
            "status": fm.get("status", it["status"]),
            "updated": fm.get("updated", "") or it["updated"],
        })
    entries.sort(key=lambda e: (TYPE_ORDER.index(e["type"]) if e["type"] in TYPE_ORDER else 99, e["module"], e["rel"]))
    stat = {}
    for e in entries:
        stat[e["status"]] = stat.get(e["status"], 0) + 1
    L = []
    L.append("---")
    L.append("type: INDEX")
    L.append("module: ")
    L.append("status: done")
    L.append("slices: []")
    L.append("aligns: []")
    L.append("updated: %s" % today)
    L.append("---")
    L.append("")
    L.append("# MES 文档总账（INDEX）")
    L.append("")
    L.append("> 自动生成，手改会被覆盖 · 再生成：`python .workbuddy/scripts/add_frontmatter.py --reindex`")
    L.append("> 生成：%s · 共 %d 篇" % (today, len(entries)))
    L.append("")
    L.append("## 状态汇总")
    L.append("")
    L.append("| 状态 | 篇数 |")
    L.append("|------|------|")
    for s in sorted(stat):
        L.append("| %s | %d |" % (s, stat[s]))
    L.append("")
    L.append("## 按类型索引")
    cur = None
    for e in entries:
        if e["type"] != cur:
            cur = e["type"]
            n = sum(1 for x in entries if x["type"] == cur)
            L.append("")
            L.append("### %s（%d 篇）" % (cur, n))
            L.append("")
            L.append("| 文档 | 模块 | 状态 | 更新 | 路径 |")
            L.append("|------|------|------|------|------|")
        mark = "done ✅" if e["status"] == "done" else e["status"]
        L.append("| %s | %s | %s | %s | `%s` |" % (e["title"], e["module"] or "—", mark, e["updated"] or "—", e["rel"]))
    L.append("")
    with open(os.path.join(DOCS, "INDEX.md"), "wb") as f:
        f.write("\n".join(L).encode("utf-8"))
    return len(entries)


def main():
    reindex = "--reindex" in sys.argv
    items = scan()
    if reindex:
        n = build_index(items)
        print("reindexed: %d docs -> docs/INDEX.md" % n)
        return
    changed, skipped = add_frontmatter(items)
    for it in changed:
        print("[fm] %-8s %-6s %-12s %s" % (it["type"], it["module"] or "-", it["status"], it["rel"]))
    print("---")
    print("frontmatter added: %d, already had: %d" % (len(changed), len(skipped)))
    n = build_index(items)
    print("index built: %d docs -> docs/INDEX.md" % n)


if __name__ == "__main__":
    main()
