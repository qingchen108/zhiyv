# 智愈（SmartMed）— 技术决策上下文

> 本文件记录所有已确认的技术决策，作为团队开发的单一参考来源。
> 最后更新：2026-07-30

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
