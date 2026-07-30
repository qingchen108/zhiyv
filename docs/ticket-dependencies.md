# Ticket 依赖关系图

> 数据来源：`.scratch/smartmed/issues/` 下 16 个 ticket 文件（`000` 规格书 + `01`–`15` 实现 ticket），依赖关系取自各文件头部 `**Blocked by:**` 字段。
> 生成日期：2026-07-30。完成进度以 git 分支 `feat/NN-*` 是否合并到 master 为准。

## 总览

- **Tickets 总数**：16 个文件 = 1 个规格书（`000`）+ 15 个实现 ticket（`01`–`15`）。
- **完成进度**：2/15 已完成（`01` 基础设施、`02` 后端骨架鉴权，均已合并 master 且经 code-review 修复），剩余 13 个未开始。
- **结构**：以 `02` 为枢纽的 DAG；6 个 ticket（`06`/`08`/`10`/`11`/`12`/`13`）有**双重依赖**，是关键汇合点。
- **当前可立即并行启动**：`03`、`07`、`09`（均只依赖已完成的 `02`）。

## 图 1：依赖关系 DAG（核心图）

箭头方向 = 执行顺序（前置 → 后继），即 `Blocked by` 的反向。

```mermaid
graph TD
    S(["000 实现规格书<br/>Spec 源头"])
    T01["01 基础设施<br/>Docker·PG·Redis·Neo4j"]
    T02["02 后端骨架与鉴权<br/>SpringBoot·JWT·路径分权"]
    T03["03 科室/医生/药品<br/>B端 CRUD + 管理页"]
    T04["04 排班与号源<br/>日历排班 + Redis 同步"]
    T05["05 挂号全链路<br/>草稿·Lua 原子扣减·并发"]
    T06["06 B端医生工作台<br/>接诊·开方·禁忌检测"]
    T07["07 C端小程序基础<br/>档案·成员·TabBar"]
    T08["08 C端记录与档案<br/>挂号/问诊/处方/订单"]
    T09["09 Agent 框架<br/>LangGraph·SSE·工具骨架"]
    T10["10 C端 AI 对话页<br/>气泡·SSE·确认卡片"]
    T11["11 Agent 智能导诊<br/>症状->科室->医生"]
    T12["12 Agent 预约挂号<br/>排班查询·草稿·确认"]
    T13["13 Agent 预问诊/处方解读<br/>摘要·过敏风险·解读"]
    T14["14 Agent 购药/用药提醒<br/>药店对比·订单·提醒"]
    T15["15 Agent 情感识别<br/>焦虑/疼痛/困惑/满意"]

    S --> T01
    T01 --> T02
    T02 --> T03
    T02 --> T07
    T02 --> T09
    T03 --> T04
    T04 --> T05
    T03 --> T06
    T05 --> T06
    T05 --> T08
    T06 --> T08
    T07 --> T10
    T09 --> T10
    T09 --> T11
    T04 --> T11
    T11 --> T12
    T05 --> T12
    T09 --> T13
    T06 --> T13
    T13 --> T14
    T12 --> T15

    classDef spec fill:#7c3aed,stroke:#5b21b6,color:#fff,stroke-width:2px,stroke-dasharray: 5 4
    classDef done fill:#10b981,stroke:#047857,color:#fff,stroke-width:3px
    classDef merge fill:#fef3c7,stroke:#d97706,color:#7c2d12,stroke-width:2px
    classDef todo fill:#f1f5f9,stroke:#64748b,color:#334155

    class S spec
    class T01,T02 done
    class T06,T08,T10,T11,T12,T13 merge
    class T03,T04,T05,T07,T09,T14,T15 todo
```

**图例**：🟪紫虚线 = 规格书 ｜ 🟩绿粗边 = 已完成 ｜ 🟨橙边 = 双依赖汇合点（关键卡点）｜ ⬜灰 = 未开始

## 图 2：按四端分组的泳道视图（模块归属 + 跨端依赖）

同一泳道内可串行推进，跨泳道边即模块间的握手点。

```mermaid
graph LR
    subgraph Infra["基础设施"]
        T01["01 基础设施 ✅"]
    end
    subgraph Backend["后端 Backend"]
        T02["02 鉴权 ✅"]
        T05["05 挂号并发"]
    end
    subgraph Admin["B端 web-admin"]
        T03["03 科室/医生/药品"]
        T04["04 排班与号源"]
        T06["06 医生工作台"]
    end
    subgraph Mini["C端 miniapp"]
        T07["07 小程序基础"]
        T08["08 记录与档案"]
        T10["10 AI 对话页"]
    end
    subgraph AgentS["Agent (Python)"]
        T09["09 框架"]
        T11["11 智能导诊"]
        T12["12 预约挂号"]
        T13["13 预问诊/处方"]
        T14["14 购药/提醒"]
        T15["15 情感识别"]
    end

    T01 --> T02
    T02 --> T03
    T02 --> T07
    T02 --> T09
    T03 --> T04
    T04 --> T05
    T03 --> T06
    T05 --> T06
    T05 --> T08
    T06 --> T08
    T07 --> T10
    T09 --> T10
    T09 --> T11
    T04 --> T11
    T11 --> T12
    T05 --> T12
    T09 --> T13
    T06 --> T13
    T13 --> T14
    T12 --> T15
```

## 依赖明细表

| Ticket | Blocked by | 依赖数 | 端归属 |
|--------|-----------|:---:|--------|
| 01 基础设施 | - | 0 | 基建 |
| 02 后端骨架鉴权 | 01 | 1 | 后端 |
| 03 科室/医生/药品 | 02 | 1 | 后端+B端 |
| 04 排班与号源 | 03 | 1 | 后端+B端 |
| 05 挂号全链路 | 04 | 1 | 后端 |
| 06 医生工作台 | **03, 05** | 2 | B端 |
| 07 小程序基础 | 02 | 1 | C端 |
| 08 C端记录与档案 | **05, 06** | 2 | C端 |
| 09 Agent 框架 | 02 | 1 | 后端+Agent |
| 10 C端 AI 对话页 | **07, 09** | 2 | C端 |
| 11 Agent 智能导诊 | **09, 04** | 2 | Agent |
| 12 Agent 预约挂号 | **11, 05** | 2 | Agent |
| 13 Agent 预问诊/处方 | **09, 06** | 2 | Agent |
| 14 Agent 购药/提醒 | 13 | 1 | Agent |
| 15 Agent 情感识别 | 12 | 1 | Agent |

## 四端目录归属

| 目录 | 起步 ticket | 后续迭代 ticket |
|------|------------|----------------|
| `backend/` | 02 | 03 / 04 / 05 / 06 / 09 / 13 / 14 |
| `web-admin/` | 03 | 04 / 06 |
| `miniapp/` | 07 | 08 / 10 |
| `agent/` | 09 | 11 / 12 / 13 / 14 / 15 |

四个目录均由 `01` 基础设施 ticket 占位（含 `README.md` + `.gitkeep`），实际骨架由各自起步 ticket 初始化。

## 关键洞察

1. **关键路径（决定最短工期）**：`01 → 02 → 03 → 04 → 05 → 06 → 13 → 14`（8 个 ticket 串成最长链）。任何一环卡住都直接影响收尾，其中 `06` 和 `13` 是双依赖汇合点，风险最高。

2. **三条并行主干**（`02` 完成后分叉）：
   - 后端/B端主线：`03 → 04 → 05 → 06`（串行，最重）
   - C端支线：`07 → 10`（较轻，等 `09` 汇合）
   - Agent 支线：`09 → 11 / 13`（中等，等 `04` / `06` 汇合）

3. **汇合点 = 联调风险点**：`10`（C端+Agent）、`11`（Agent+后端排班）、`12`（Agent+后端挂号）、`13`（Agent+B端工作台）--这些 ticket 同时依赖两个不同端的产出，是跨模块握手最容易出现接口不对齐的地方，建议前置 ticket 收尾时就锁定接口契约。

## 相关 ADR

- `docs/adr/0001-pgvector-dimension-1536.md`：向量维度锁 1536（对齐通义千问 text-embedding-v2），对应 `01` 的 pgvector 表预建。
- `docs/adr/0002-drug-pharmacy-stock-bridge-table.md`：新增 `drug_pharmacy_stock` 桥表（第 20 张表），承载药店-药品-库存-定价，对应 `14` 购药对比数据来源。
- `docs/adr/0003-auth-single-jwt-typ-claim.md`：单种 JWT + `typ` 声明 + 路径分权鉴权模型，对应 `02` 鉴权实现。

## 维护说明

- 本图由 ticket 文件的 `Blocked by` 字段自动还原，**新增/调整 ticket 依赖时需同步更新本文档**。
- 完成状态以 git 分支 `feat/NN-*` 是否合并到 master 为准；合并后请把对应节点在图 1 中从 `todo`/`merge` 类移到 `done` 类，并在标题加 ✅。
