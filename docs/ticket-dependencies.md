# Ticket 依赖关系图

> 数据来源：`.scratch/smartmed/issues/` 下 17 个文件（`000` 规格书 + `01`–`15` 实现 ticket + `08b` 为 `08` 的追加 bug issue，非独立实现 ticket），依赖关系取自各文件头部 `**Blocked by:**` 字段。
> 生成日期：2026-07-30；最近更新：2026-08-03。完成进度以 git 分支 `feat/NN-*` 是否合并到 master 为准。

## 总览

- **Tickets 总数**：17 个文件 = 1 个规格书（`000`）+ 15 个实现 ticket（`01`–`15`）+ 1 个追加 bug issue（`08b`，从属于 `08`）。
- **完成进度**：11/15 已完成（`01`–`11`+`12`，均已合并 master），剩余 4 个未开始。
- **结构**：以 `02` 为枢纽的 DAG；6 个双依赖 ticket（`06`/`08`/`10`/`11`/`12`/`13`）中 `06`/`08`/`10` 已完成，剩余 `11`/`12`/`13` 为关键汇合点。
- **当前可立即并行启动**：`11`、`13`（`11` 依赖的 `09`/`04` 均已就绪；`13` 依赖的 `09`/`06` 均已就绪）。

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
    T07["07 C端小程序基础 ✅<br/>档案·成员·TabBar"]
    T08["08 C端记录与档案 ✅<br/>挂号/问诊/处方/订单"]
    T09["09 Agent 框架 ✅<br/>LangGraph·SSE·工具骨架"]
    T10["10 C端 AI 对话页 ✅<br/>气泡·SSE·确认卡片"]
    T11["11 Agent 智能导诊 ✅<br/>症状->科室->医生"]
    T12["12 Agent 预约挂号 ✅<br/>排班查询·草稿·确认"]
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
    class T01,T02,T03,T04,T05,T06,T07,T08,T09,T10,T11,T12 done
    class T11,T13 merge
    class T14,T15 todo
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
        T05["05 挂号并发 ✅"]
    end
    subgraph Admin["B端 web-admin"]
        T03["03 科室/医生/药品 ✅"]
        T04["04 排班与号源 ✅"]
        T06["06 医生工作台 ✅"]
    end
    subgraph Mini["C端 miniapp"]
        T07["07 小程序基础 ✅"]
        T08["08 记录与档案 ✅"]
        T10["10 AI 对话页 ✅"]
    end
    subgraph AgentS["Agent (Python)"]
        T09["09 框架 ✅"]
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

| Ticket | Blocked by | 依赖数 | 端归属 | 状态 |
|--------|-----------|:---:|--------|------|
| 01 基础设施 | - | 0 | 基建 | ✅ 完成 |
| 02 后端骨架鉴权 | 01 | 1 | 后端 | ✅ 完成 |
| 03 科室/医生/药品 | 02 | 1 | 后端+B端 | ✅ 完成 |
| 04 排班与号源 | 03 | 1 | 后端+B端 | ✅ 完成 |
| 05 挂号全链路 | 04 | 1 | 后端 | ✅ 完成 |
| 06 医生工作台 | **03, 05** | 2 | B端 | ✅ 完成 |
| 07 小程序基础 | 02 | 1 | C端 | ✅ 完成 |
| 08 C端记录与档案 | **05, 06** | 2 | C端 | ✅ 完成 |
| 09 Agent 框架 | 02 | 1 | 后端+Agent | ✅ 完成 |
| 10 C端 AI 对话页 | **07, 09** | 2 | C端 | ✅ 完成 |
| 11 Agent 智能导诊 | **09, 04** | 2 | Agent | ✅ 完成 |
| 12 Agent 预约挂号 | **11, 05** | 2 | Agent | ✅ 完成 |
| 13 Agent 预问诊/处方 | **09, 06** | 2 | Agent | ⏳ 可启动 |
| 14 Agent 购药/提醒 | 13 | 1 | Agent | 待 13 |
| 15 Agent 情感识别 | 12 | 1 | Agent | 待 12 |

## 四端目录归属

| 目录 | 起步 ticket | 后续迭代 ticket |
|------|------------|----------------|
| `backend/` | 02 | 03 / 04 / 05 / 06 / 09 / 13 / 14 |
| `web-admin/` | 03 | 04 / 06 |
| `miniapp/` | 07 | 08 / 10 |
| `agent/` | 09 | 11 / 12 / 13 / 14 / 15 |

四个目录均由 `01` 基础设施 ticket 占位（含 `README.md` + `.gitkeep`），实际骨架由各自起步 ticket 初始化。

## 关键洞察

1. **关键路径（决定最短工期）**：`01 → 02 → … → 10` 已全部完成；剩余最长链为 `12 → 15`（2 个 ticket，12 已完成），次长链 `13 → 14`（2 个 ticket）。汇合点 `13`（Agent+B端）是剩余风险最高处。

2. **剩余并行主干**（`01–10` 完成后）：
   - Agent 支线：`11` 可立即启动（依赖 `09`/`04` 已就绪） → `12` → `15`
   - Agent 支线：`13` 可立即启动（依赖 `09`/`06` 已就绪） → `14`
   - C端支线：已全部完成（`07`/`08`/`10` 已合并）

3. **汇合点 = 联调风险点**：`13`（Agent+B端工作台）是剩余唯一跨端汇合点；`11`/`12` 的后端依赖（`04`/`05`/`09`）均已就绪，风险较低。`13` 实现前需确认 pre_diagnosis 写入结构（`06` 已按 CONTEXT §10 留 null 占位）。

## 相关 ADR

- `docs/adr/0001-pgvector-dimension-1536.md`：向量维度锁 1536（对齐通义千问 text-embedding-v2），对应 `01` 的 pgvector 表预建。
- `docs/adr/0002-drug-pharmacy-stock-bridge-table.md`：新增 `drug_pharmacy_stock` 桥表（第 20 张表），承载药店-药品-库存-定价，对应 `14` 购药对比数据来源。
- `docs/adr/0003-auth-single-jwt-typ-claim.md`：单种 JWT + `typ` 声明 + 路径分权鉴权模型，对应 `02` 鉴权实现。
- `docs/adr/0004-b-login-by-phone.md`：B 端登录改手机号 + 首登改密，对应 `03` 登录返工。
- `docs/adr/0005-doctor-account-linkage-single-source-phone.md`：医生账号联动 + 手机号单源镜像，对应 `03`。
- `docs/adr/0006-physical-delete-with-preflight-check.md`：物理删除 + 前置引用检查，对应 `03` 三实体删除。
- `docs/adr/0007-b-web-jwt-localstorage.md`：B 端 JWT 存 localStorage 方案，对应 `03` B 端工程初始化。
- `docs/adr/0008-flyway-database-migration.md`：Flyway 接管数据库迁移，对应 `01`/`03` 迁移脚本。
- `docs/adr/0009-schedule-slot-management.md`：排班时段枚举 + 号源管理规则，对应 `04`。
- `docs/adr/0010-registration-family-member-dual-fk.md`：家人挂号双 FK 口径，对应 `05`。
- `docs/adr/0011-consultation-auto-create-on-registration.md`：确认挂号自动创建问诊，对应 `06`。
- `docs/adr/0012-contraindication-check-strategy.md`：开方禁忌检测策略，对应 `06`。
- `docs/adr/0013-b-jwt-dual-token-session.md`：B 端 JWT 双 token 与会话管理（access+refresh + Redis 会话 + 实时吊销），对应 `02`。
- `docs/adr/0014-sse-event-protocol.md`：对话事件协议与 Java 网关（SSE → WS 传输容器修订），对应 `09`/`10`。
- `docs/adr/0015-agent-tool-boundary.md`：Agent 工具边界（只建草稿，不替用户确认），对应 `09`/`12`/`14`。
- `docs/adr/0016-agent-tool-handler-reuse.md`：Agent 工具 handler 复用既有 Service 层，对应 `12`/`14`。
- `docs/adr/0017-order-confirm-stock-reminder.md`：购药确认接口、库存扣减与用药提醒自动生成，对应 `14`。
- `docs/adr/0018-integration-test-time-window-fix.md`：集成测试时间窗口修复策略（方案 B + ScheduleFixture），对应 `08b`。

## 维护说明

- 本图由 ticket 文件的 `Blocked by` 字段自动还原，**新增/调整 ticket 依赖时需同步更新本文档**。
- 完成状态以 git 分支 `feat/NN-*` 是否合并到 master 为准；合并后请把对应节点在图 1 中从 `todo`/`merge` 类移到 `done` 类，并在标题加 ✅。
