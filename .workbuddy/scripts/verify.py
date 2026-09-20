import os

DOCS = r"D:\java\xm\2026_07\MES\docs"
OUT = r"D:\java\xm\2026_07\MES\.workbuddy\scripts\verify_out.txt"
BOM = b"\xef\xbb\xbf"
skipd = {"_templates"}
skipf = {"README.md", "INDEX.md"}

total = 0
nofm = []
types = {}
for root, dirs, files in os.walk(DOCS):
    dirs[:] = [d for d in dirs if d not in skipd and not d.startswith(".")]
    for fn in files:
        if fn.endswith(".md") and fn not in skipf:
            total += 1
            raw = open(os.path.join(root, fn), "rb").read()
            if raw.startswith(BOM):
                raw = raw[3:]
            if not raw.startswith(b"---"):
                nofm.append(os.path.relpath(os.path.join(root, fn), DOCS))
            else:
                first = raw.decode("utf-8", errors="replace").splitlines()
                for l in first[1:8]:
                    if l.startswith("type:"):
                        t = l.split(":", 1)[1].strip()
                        types[t] = types.get(t, 0) + 1
                        break

lines = ["total md (excl templates/readme/index): %d" % total,
         "without frontmatter: %d" % len(nofm)]
for p in nofm:
    lines.append("  MISSING: %s" % p)
lines.append("type distribution:")
for t, n in sorted(types.items(), key=lambda x: -x[1]):
    lines.append("  %-10s %d" % (t, n))
idx = os.path.join(DOCS, "INDEX.md")
lines.append("INDEX exists: %s" % os.path.exists(idx))
if os.path.exists(idx):
    lines.append("INDEX size: %d bytes" % os.path.getsize(idx))

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("written")
