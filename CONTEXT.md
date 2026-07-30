# 智愈（SmartMed）— 技术决策上下文

> 本文件记录所有已确认的技术决策，作为团队开发的单一参考来源。
> 最后更新：2026-07-31

---

## 1. 项目结构

| 决策项 | 结论 |
|--------|------|
| 仓库模式 | Monorepo |
| 顶层目录 | `backend/` `web-admin/` `miniapp/` `agent/` |
| 开发环境 | Docker Compose 一键拉起（PostgreSQL + Redis + Neo4j） |

## 2. 后端（backend/）

| 决策项 | 结论 |
|--------|------|
| 框架 | Spring Boot 3.x |
| JDK | Java 17 |
| 构建工具 | Maven |
| ORM | MyBatis-Plus |
| 数据库迁移 | Flyway（应用启动时自动执行 `db/migration/V*__*.sql`，见 ADR-0008） |
| 数据库 | PostgreSQL + pgvector 扩展 |
| 缓存 | Redis |
| 主键策略 | 自增 BIGINT（BIGSERIAL） |
| 删除策略 | 物理删除（直接 DELETE） |
| 响应格式 | `{ "code": 200, "message": "success", "data": {...} }` |
| 鉴权 | JWT 无状态，`Authorization: Bearer xxx`；B 端手机号+密码登录（非用户名），sys_user.phone 为登录键（UNIQUE），见 ADR-0004 |
| 权限模型 | role 枚举（ADMIN / DOCTOR）+ `@PreAuthorize` 注解 |
| 用户模型 | sys_user（登录账号）与 doctor（业务实体）分离，sys_user.doctor_id 关联；新建/删除 doctor 同事务建/删 sys_user，见 ADR-0005 |
| 删除策略 | 物理删除（直接 DELETE）+ 前置引用检查（有引用则 409 拒绝），非级联删，见 ADR-0006 |

## 3. C 端（miniapp/）

| 决策项 | 结论 |
|--------|------|
| 平台 | 支付宝小程序 |
| 技术 | TypeScript + Ant Design Mini + AntV F2 |
| 登录方式 | 启动时调 `/api/c/auth/demo-login` 获取 JWT，无需支付宝授权 |
| 流式对话 | SSE（Server-Sent Events），Java 做代理，小程序不直连 Python |

## 4. B 端（web-admin/）

| 决策项 | 结论 |
|--------|------|
| 框架 | Umi + React + TypeScript |
| UI 库 | Ant Design + AntV |
| 状态管理 | Zustand |
| 样式 | Less |
| 登录 | 账号密码登录获取 JWT（手机号+密码，非用户名） |

## 5. AI Agent（agent/）

| 决策项 | 结论 |
|--------|------|
| 语言 | Python |
| 编排框架 | LangGraph + LangChain |
| LLM | 国内模型（通义千问/智谱/文心）或 AI 聚合平台（火山引擎等） |
| 与 Java 通信 | HTTP REST，Java 暴露 `/api/agent/tools/*` |
| Agent 鉴权 | 请求头 `X-Agent-Secret`（共享密钥，环境变量配置） |
| 患者身份 | Java 转发时注入 `X-Patient-Id`，Python 调工具时原样带回 |
| 核心原则 | Python 不直接访问任何数据库，所有数据访问经 Java API |

## 6. 知识图谱（Neo4j）

| 决策项 | 结论 |
|--------|------|
| 访问方式 | 仅 Java 直连 Neo4j，Python 通过 Java API 间接查询 |
| 节点 | Symptom、Disease、Department、Drug、Allergen |
| 关系 | INDICATES、BELONGS_TO、TREATED_BY、INTERACTS_WITH、CONTAINS、CONTRAINDICATED_IN |
| 数据规模 | 约 50 症状 + 30 疾病（聚焦常见病种） |

## 7. 号源与并发

| 决策项 | 结论 |
|--------|------|
| 号源主数据 | PostgreSQL schedule 表（total_slots / remaining_slots） |
| 热缓存 | Redis（排班发布时同步，扣减在 Redis 做） |
| 扣减方式 | Redis Lua 脚本原子操作 |
| 防刷 | 同用户同排班 5 秒频率限制 |
| 挂号草稿 | Redis + TTL 30min（过期自动作废） |
| 购药草稿 | Redis + TTL 30min |
| 时段模型 | 枚举班次：MORNING(08:00-12:00) / AFTERNOON(14:00-17:30) / EVENING(18:00-21:00)，见 ADR-0009 |
| 冲突校验 | UNIQUE(doctor_id, schedule_date, time_period)，同医生同日同班次不可重复 |
| 发布流程 | 创建即发布即写 Redis，无草稿态；status 仅 PUBLISHED / SUSPENDED |
| 停诊/恢复 | 停诊 DEL Redis key；恢复 SET key = DB remaining_slots |
| 删除约束 | 有挂号引用 → 409 仅可停诊；无引用 → 物理删除 + DEL key |
| 批量复制 | 周复制：total 照抄、remaining = total（全新放号），已有组合跳过 |
| 手动调整 | 允许 remaining > total（加号），不可 < 0 |
| 修改排班 | 全字段可编辑；new_total ≥ 已用数（否则 400）；仍需 UNIQUE + 日期窗口校验 |
| 周起点 | 周一 |
| 日期窗口 | schedule_date ∈ [today, today+14]，不可排过去、最多排 14 天 |
| Redis 容错 | @Async 重试一次 + ERROR 日志；C 端 Redis miss 回查 PG 回填 |
| 挂号草稿 | 两段式：创建草稿（Redis TTL 30min + SHA-256 confirmToken）→ 确认消费（DEL key + Lua 扣减） |
| 草稿 key | `reg_draft:{operatorPatientId}:{visitorId}:{scheduleId}`，visitorId = "self" 或 "fm:{familyMemberId}" |
| 防刷 key | `reg_ratelimit:{visitorId}:{scheduleId}` TTL 5s，按实际就诊人维度 |
| Lua 扣减返回值 | 1=成功 / -1=号源不足 / -2=key 不存在（停诊/删除） |
| Redis-PG 一致性 | Redis 先扣，PG 写入失败则补偿 INCR + ERROR 日志 |
| 重复挂号 | 实际就诊人 + schedule_id 存在 REGISTERED/VISITED → 400 拒绝 |
| 家人挂号 | registration.patient_id=操作人，family_member_id=实际就诊人（nullable），见 ADR-0010 |
| 取消规则 | 就诊前 2h 以上可取消（基准=schedule_date+start_time）；停诊时只更新 PG 不写 Redis |
| VISITED 流转 | 医生手动标记 + @Scheduled 每 10min 兜底（schedule_date+end_time < now） |
| reg_no 生成 | DB 序列全局递增，格式 REG+yyyyMMdd+LPAD(seq,3,'0') |
| 挂号凭证 | 确认成功返回完整挂号记录 JSON（regNo/doctorName/departmentName/scheduleDate/timePeriod/status） |

## 8. 对话链路

```
小程序 → Java /api/c/chat/stream (SSE)
       → Java 验 JWT + 注入 X-Patient-Id
       → 转发 Python /agent/chat (HTTP stream)
       → Python LangGraph 执行 + 工具调用
       → 工具调用: Python → Java /api/agent/tools/* (带 X-Agent-Secret + X-Patient-Id)
       → Java 透传 SSE 事件 → 小程序逐字渲染
```

## 9. 开发模式

| 决策项 | 结论 |
|--------|------|
| 团队 | 多人分工（后端/B端/C端/Agent 并行） |
| 协作方式 | 接口契约先行，各端基于契约并行开发 |
| 总工期 | 2 周（10 个工作日） |

---

## 术语表（Glossary）

| 术语 | 定义 |
|------|------|
| 号源 | 某个排班时段可被预约的名额数量 |
| 排班（Schedule） | 医生在某天某时段出诊的安排，包含号源总数 |
| 草稿（Draft） | 两段式写操作的第一阶段产物，未真正写库，30分钟过期 |
| 预问诊 | AI 在医生接诊前收集并整理的病情摘要 |
| 工具（Tool） | Agent 可调用的 Java API，用于完成业务操作 |
| 知识图谱 | Neo4j 中症状-疾病-科室-药品-禁忌的关联网络 |
| 演示账号 | C 端预设患者身份，打开即登录，无需授权 |
| 医院 | 种子数据预设的唯一一家三甲医院，B 端不提供医院管理页面，所有科室/医生/排班均归属此医院 |
| 药店库存 | 某药店对某药品的备货记录（价格、库存、配送时效），drug_pharmacy_stock 表承载，购药对比的数据来源 |
| 登录方式 | B 端用手机号+密码登录，sys_user.phone 为登录键（NOT NULL UNIQUE）；username 降级为展示标签（ADMIN 固定"管理员"，DOCTOR 取姓名），见 ADR-0004 |
| 首登改密 | 新建账号 must_change_password=true，首次登录强制走 `/api/b/auth/change-password` 改密后方可使用，见 ADR-0005 |
| 医生账号联动 | 新建 doctor 同事务建 sys_user（role=DOCTOR，密码默认 123456，must_change_password=true）；删 doctor 同事务删 sys_user，见 ADR-0005 |
| 单源镜像 | 手机号只存 sys_user.phone，doctor 表不存 phone，展示靠 JOIN sys_user 取，见 ADR-0005 |
| 班次（Time Period） | 排班时段枚举：MORNING / AFTERNOON / EVENING，后端自动映射起止时间，见 ADR-0009 |
| 停诊（Suspend） | 排班下线操作，DEL Redis key 使 C 端不可挂号，DB 数据保留，可恢复 |
| 加号 | 手动增加 remaining_slots 使其超过 total_slots 的操作，表示临时扩容 |
| 周复制 | 将源周排班批量复制到目标周，total 照抄、remaining 重置为 total，已有组合跳过 |
| 挂号凭证（Registration Voucher） | 确认挂号成功后返回的完整挂号记录 JSON，含 reg_no、医生、科室、日期、班次、状态 |
| 确认令牌（Confirm Token） | 草稿创建时生成的 SHA-256 一次性凭证，确认时比对，草稿消费后随 key 删除失效 |
| 操作人（Operator） | 发起挂号操作的登录患者（JWT 身份），帮家人挂时操作人≠就诊人 |
| 实际就诊人（Visitor） | 真正接受诊疗的个体，可以是操作人本人或其健康档案中的家庭成员 |
| 挂号单号（reg_no） | 挂号记录唯一标识，格式 REG+yyyyMMdd+序列号，DB 序列全局递增 |
