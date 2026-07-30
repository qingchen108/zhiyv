# 智愈（SmartMed）— 实现规格书

**Status**: ready-for-agent
**Labels**: spec, full-project
**Blocking**: 无（本 spec 是所有 ticket 的源头）

---

## Problem Statement

传统医疗流程中，患者面临导诊靠猜、挂号靠抢、问诊重复、处方看不懂、买药跑腿、看完即结束六大痛点。需要一个以 AI Agent 为"第一联系人"的医疗平台，让 Agent 不仅能对话，还能调用真实 API 完成挂号、问诊、购药等业务闭环。

## Solution

构建 B+C 深度联动的智能医疗平台：

- **C 端**（支付宝小程序）：AI Agent 对话页为核心，覆盖导诊→挂号→问诊→处方解读→购药→用药提醒全链路
- **B 端**（Web 管理后台）：科室/医生/排班/号源/药品管理 + 医生工作台（接诊、开方）
- **后端**（Spring Boot）：统一 BFF，业务 API + Agent 网关 + 号源并发控制
- **AI Agent**（Python LangGraph）：多轮对话编排 + 工具调用 + 知识图谱推理 + 情感识别

## User Stories

### C 端 — 患者

1. As a 患者, I want 打开小程序即自动登录（演示账号）, so that 无需注册即可体验全部功能
2. As a 患者, I want 首次使用时填写个人档案（姓名、性别、出生日期、过敏史）, so that AI 能基于我的信息做个性化推荐
3. As a 患者, I want 在对话页用自然语言描述症状, so that AI 能理解我的需求并主动追问细节
4. As a 患者, I want AI 根据症状推荐科室并说明理由, so that 我不再挂错科
5. As a 患者, I want AI 推荐 3 位匹配医生（附职称、擅长、好评率）, so that 我能做出知情选择
6. As a 患者, I want 在对话中确认挂号（两段式：草稿→确认）, so that 不会误操作
7. As a 患者, I want 挂号成功后收到凭证卡片和就诊提醒, so that 不会忘记就诊时间
8. As a 患者, I want AI 在挂号前整理预问诊摘要（主诉、现病史、过敏史）, so that 医生不用从头问
9. As a 患者, I want 在小程序内与医生图文对话, so that 不用跑医院就能看病
10. As a 患者, I want AI 用通俗语言解读处方（药品作用、吃法、注意事项）, so that 看得懂医生开了什么
11. As a 患者, I want AI 检测到过敏风险时高亮警告并阻断购药, so that 不会买到过敏药
12. As a 患者, I want AI 推荐附近药店（价格、距离、时效对比）, so that 买到性价比最高的药
13. As a 患者, I want 确认购药后自动设置用药提醒, so that 按时吃药不忘
14. As a 患者, I want 查看挂号记录（列表+详情+取消）, so that 管理自己的预约
15. As a 患者, I want 查看问诊记录（摘要、诊断、处方、对话）, so that 回顾就诊历史
16. As a 患者, I want 查看处方列表和购药订单, so that 追踪用药和配送
17. As a 患者, I want 管理多成员健康档案（父母、子女等）, so that 一个账号管全家
18. As a 患者, I want 对话中看到工具调用轨迹（"正在查询知识图谱..."）, so that 知道 AI 在做什么
19. As a 患者, I want 对话流式输出（逐字显示）, so that 体验流畅不卡顿
20. As a 患者, I want 紧急症状（胸痛/大出血/呼吸困难）被优先识别并建议立即就医, so that 不耽误急救

### B 端 — 管理员

21. As an 管理员, I want 用账号密码登录后台, so that 安全访问管理功能
22. As an 管理员, I want 管理科室（CRUD）, so that 维护医院科室信息
23. As an 管理员, I want 管理医生（CRUD + 关联科室）, so that 维护医生信息
24. As an 管理员, I want 日历视图排班（选医生、日期、时段、号源数）, so that 直观安排出诊
25. As an 管理员, I want 批量排班（一键复制上周）, so that 不用每周重复操作
26. As an 管理员, I want 查看号源总览（按科室/医生/日期筛选）, so that 掌握号源使用情况
27. As an 管理员, I want 手动调整号源和停诊处理, so that 应对紧急情况
28. As an 管理员, I want 管理药品基础信息（CRUD）, so that 维护药品数据

### B 端 — 医生

29. As a 医生, I want 查看今日待接诊列表（含预问诊摘要）, so that 提前了解患者病情
30. As a 医生, I want 查看问诊详情（预问诊摘要 + 对话区 + 诊断输入）, so that 高效接诊
31. As a 医生, I want 与患者图文对话, so that 进一步了解病情
32. As a 医生, I want 开具处方（选药品、设用法用量）, so that 完成诊疗
33. As a 医生, I want 开方时自动检测药物禁忌和过敏风险, so that 避免医疗事故
34. As a 医生, I want 查看患者历史病历, so that 了解既往情况
35. As a 医生, I want 保存常用处方模板并一键填充, so that 提高开方效率
36. As a 医生, I want 编辑个人信息（擅长、简介）, so that 保持资料更新

### AI Agent

37. As an AI Agent, I want 识别用户意图（导诊/挂号/问诊/购药/闲聊）, so that 进入正确的处理流程
38. As an AI Agent, I want 多轮追问症状细节直到信息充分, so that 做出准确推荐
39. As an AI Agent, I want 调用知识图谱查询症状→疾病→科室→药品关系, so that 推荐有据可依
40. As an AI Agent, I want 推荐时附带可解释理由, so that 用户信任推荐结果
41. As an AI Agent, I want 识别用户情绪（焦虑/疼痛/困惑）并调整话术, so that 提供情感支持
42. As an AI Agent, I want 所有写操作走草稿→确认两段式, so that 不会误操作用户数据
43. As an AI Agent, I want 所有 AI 输出标注"AI 建议仅供参考", so that 合规免责

## Implementation Decisions

### 架构

- Monorepo 结构：`backend/` `web-admin/` `miniapp/` `agent/` 四端同仓
- Java 做统一 BFF：C 端、B 端、Agent 工具调用全部经 Java 路由
- Python Agent 不直接访问任何数据库，所有数据访问经 Java `/api/agent/tools/*`
- 对话链路：小程序 → Java SSE 代理 → Python LangGraph → 工具回调 Java → SSE 透传

### 后端

- Spring Boot 3.x + Java 17 + Maven + MyBatis-Plus
- PostgreSQL（业务数据）+ Redis（缓存/号源/草稿/防刷）+ Neo4j（知识图谱）
- 主键：自增 BIGINT；删除：物理删除
- 统一响应：`{ code, message, data }`，code 取值为 HTTP 状态码（200/400/401/403/500）
- 鉴权：JWT 无状态；权限：role 枚举（ADMIN/DOCTOR）+ @PreAuthorize
- sys_user（登录账号）与 doctor（业务实体）分离

### 号源并发

- PG schedule 表存主数据（total_slots / remaining_slots）
- 排班发布时同步到 Redis，扣减在 Redis 用 Lua 脚本原子执行
- 防刷：同用户同排班 5 秒频率限制
- 挂号/购药草稿：Redis + TTL 30min

### C 端

- 支付宝小程序 + TypeScript + Ant Design Mini
- 启动时调 `/api/c/auth/demo-login` 获取 JWT
- 对话页 SSE 流式输出，确认卡片嵌入对话流

### B 端

- Umi + React + TypeScript + Ant Design + Zustand + Less
- 账号密码登录获取 JWT
- 日历排班视图 + 医生工作台左右分栏布局

### AI Agent

- Python + LangGraph + LangChain
- LLM：国内模型（通义千问/智谱/文心）或 AI 聚合平台（火山引擎）
- Agent 鉴权：`X-Agent-Secret` 共享密钥
- 患者身份：Java 注入 `X-Patient-Id`，Python 原样带回，不信任 Agent 传身份

### 知识图谱

- Neo4j 节点：Symptom / Disease / Department / Drug / Allergen
- 关系：INDICATES / BELONGS_TO / TREATED_BY / INTERACTS_WITH / CONTAINS / CONTRAINDICATED_IN
- 数据规模：约 50 症状 + 30 疾病，聚焦常见病种
- 仅 Java 直连 Neo4j，Python 通过 Java API 间接查询

### 数据库（20 张表）

- 用户与档案：sys_user、patient、patient_family_member
- 医院业务：hospital、department、doctor、schedule、registration
- 问诊与处方：consultation、consultation_message、prescription、prescription_item、prescription_template
- 药品与购药：drug、pharmacy、drug_pharmacy_stock、drug_order
- AI 与提醒：chat_session、chat_message、medication_reminder
- 草稿存 Redis 不落表（registration_draft、order_draft 用 Redis Key）

### API 路径规范

- B 端：`/api/b/**`（需 ADMIN 或 DOCTOR 角色）
- C 端：`/api/c/**`（需患者 JWT）
- Agent 工具：`/api/agent/tools/**`（需 X-Agent-Secret）
- 公开：`/api/auth/**`（登录）

## Testing Decisions

### 原则

- 只测外部行为（API 输入→输出），不测内部实现细节
- 每个测试对应一个业务场景，粒度小、独立可运行
- 测试代码放在项目 `src/test/` 中，`mvn test` 一键执行

### 后端（Java）

- 核心逻辑写集成测试（MockMvc 穿透 Controller → Service → DB）
- 重点覆盖：号源扣减（Lua 原子性）、防刷限流、鉴权拦截、两段式草稿确认
- 使用 H2 内存数据库或 Testcontainers 做数据层测试
- 预计 10-15 个关键测试用例

### Agent（Python）

- Mock 掉 Java 工具接口，单独测试 LangGraph 对话编排
- 重点覆盖：意图识别准确性、工具调用顺序、紧急症状拦截
- 使用 pytest

### 前端（B 端 / C 端）

- 不写自动化测试
- 手动验证 + 联调阶段全链路走通

## Out of Scope

- 支付宝授权登录（用演示账号替代）
- 多医院管理（种子数据预设 1 家三甲医院）
- 视频/语音问诊（仅图文）
- 药师审核处方（医生开方即生效）
- 商保快赔、慢病管理计划（选题背景中的扩展方向）
- 多模态交互（舌苔/伤口照片识别）
- 异地多活、隐私计算
- 前端自动化测试（E2E / 单元测试）
- 实际上架支付宝应用商店（开发版/体验版 Demo 即可）
- 主动关怀的定时任务调度（P2，有余力再做）

## Further Notes

- 总工期 2 周（10 工作日），4 人并行：后端 / B 端 / C 端 / Agent
- 接口契约先行：先输出 API 文档，各端基于契约并行开发，第 2 周联调
- 知识图谱数据由后端同学准备 Cypher 初始化脚本
- 所有 AI 输出必须标注"AI 建议仅供参考，不能替代医生诊断"
- 紧急症状识别是医疗安全底线，必须在 Agent 和后端双重拦截
- 答辩准备 3 分钟视频 Demo，展示 Agent 对话→挂号→问诊→购药完整闭环
