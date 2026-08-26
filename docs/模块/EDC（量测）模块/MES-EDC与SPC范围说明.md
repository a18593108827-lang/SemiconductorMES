# EDC 与 SPC — 范围说明

> 用途：量测相关开工边界；避免把 SPC 当成 TrackOut 门禁前置  
> 对齐：`docs/业务清单/MES-半导体业务清单.md` §9；Track T2-7 见 `MES-Track二期功能清单.md` §3.2  
> EDC 详设：`MES-EDC功能文档.md` · `MES-EDC数据库设计.md` · `MES-EDC一期功能清单.md`  
> 现状（2026-08-24）：**EDC 一期 P0 已齐**；SPC **架构已定、未落地**  
> 更新：2026-08-24  
> 架构：`docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md` 

---

## 1. 一句话

| 概念 | 全称 | 干什么 |
|------|------|--------|
| **EDC** | Engineering Data Collection | 把量测值采进来，对照规格判合格/超规 |
| **SPC** | Statistical Process Control | 用这些数做控制图、CPK、趋势/失控报警 |
| **FDC** | Fault Detection & Classification | 设备过程量实时故障检测（另一条线，P2） |

**共用数据，不同问题**：EDC 回答「这一笔能不能过」；SPC 回答「这一段过程稳不稳」。

---

## 2. 建议落地顺序

```
① EDC 最小集（站绑定 / 录入 / Spec 判规 / Facade）
    → ② TrackOut EDC 门禁 T2-7（拒不合格出站）
        → ③ SPC 趋势预警（I-MR / Alarm；不挡门禁、不自动 Hold）
```

| 阶段 | 做不做 | 说明 |
|------|--------|------|
| EDC 最小集 | **要做**（上量测卡控前） | 门禁依赖 Facade |
| TrackOut EDC 门禁 | **要做**（EDC 之后） | `MES-Track二期功能清单.md` §3.2 / T2-7 |
| SPC | **架构已定** | 不挡门禁；OOC 只 Alarm；Hold/锁机后置 |
| FDC / 量测机自动回传 | P2 | 与 SPC 分开排期 |

**结论**：后续会做 SPC，但**不是** EDC / T2-7 的前置；先采得稳、卡得出站，再上统计监控。

---

## 3. 边界（后续建模块时遵守）

```
EDC   = 采集真相 + Spec 单笔判定 + 对 Track/SPC 的 Facade
SPC   = 读 EDC 历史点 → 控制限 / 判异 → Alarm（本期不 Hold）
Track = 只调 EDC Facade 做 Out 门禁；不画图、不存点、不问 SPC
Hold  = OOS 仍只在 EDC；OOC→Hold 后置
```

**禁止**

- Track 内存量测点或自画 SPC  
- 无 EDC 采集表、只为 SPC 另造一套点数据  
- 把「未上 SPC」当成「不能做 TrackOut 门禁」的理由  

---

## 4. SPC 最小集

以架构为准，不在本文件展开：`docs/模块/SPC（统计过程控制）模块/MES-SPC架构设计.md`。

| 项 | 本期 |
|----|------|
| 输入 | EDC 全量数值点（含 OOS） |
| 能力 | I-MR；WE1；可选连跑；n≥25 才给 Cpk |
| 输出 | Admin 趋势；`SPC_OOC` Alarm |
| 不做 | Hold/锁机、X̄-R、WE 全集、FDC、现场新入口 |

---

## 5. 关联

- `MES-EDC功能文档.md`  
- `MES-EDC数据库设计.md`  
- `MES-EDC一期功能清单.md`  
- `docs/业务清单/MES-半导体业务清单.md` §9  
- `docs/模块/Track（执行引擎）模块/MES-Track二期功能清单.md` §3.2 / T2-7  
- `docs/架构/MES-实施进度与下一步.md`  
- `MES-SPC一期功能清单.md`  
- `MES-SPC架构设计.md`  
- `docs/架构/半导MES架构设计.md`（EDC / Alarm·SPC） 
