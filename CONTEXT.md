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
| Agent 鉴权 | 请求头 `X-Agent-Secret`（共享密钥，环境变量配置），双向校验：Python→Java 调工具、Java→Python 转发 chat 均带同一密钥，两端各自校验 |
| 患者身份 | Java 转发时注入 `X-Patient-Id`（= JWT patientId claim 的操作人，不信任前端）；就诊人由工具参数 `family_member_id` 表达（self=本人），Java 工具实现沿用现有归属校验；Python 只透传不解读 |
| 核心原则 | Python 不直接访问任何数据库，所有数据访问经 Java API |
| 意图集（Intent） | 6 类：triage / registration / consultation / pharmacy / reminder / general（闲聊兜底），与 ticket 11-14 能力域一一对应；emotion 为旁路能力（影响语气），不单独成意图 |
| 图结构 | LangGraph StateGraph + 条件边（router → 各意图节点 → 汇聚），09 起预留意图节点插槽，后续 ticket 只填空不改骨架 |
| 意图路由 | 09 阶段真实 LLM 分类（验证分类质量），分类结果仅决定 prompt 模板，不触发工具调用 |
| LLM 接入 | 统一 OpenAI-compatible 接口（ChatOpenAI + base_url/api_key/model 三变量切换），不引入各家 SDK；不做 fallback，启动时连通性校验失败即清晰报错 |
| 流式生成 | `astream_events` 产出 token 增量，映射为 SSE `delta` 事件 |
| 工具清单 | 11 个：查询类 7（query_departments / query_doctors / query_schedule / query_knowledge_graph / get_medical_record / get_prescription / query_pharmacy_stock）+ 动作类 4（create_registration_draft / write_pre_diagnosis / create_order_draft / create_reminder）；confirm 类（挂号确认/下单确认）不入 Agent 工具集，前端凭卡片 action 直调 Java |
| 工具边界 | Agent 仅能创建草稿，永远不能替用户完成挂号/下单；卡片 payload 数据源为 Java 草稿响应权威 JSON，LLM 在确认链路中零参与 |
| 工具交付 | 09 声明不实现：定义文件（name/description/params schema）交付全部 11 个，Java 路由骨架 + X-Agent-Secret 校验到位，内部逻辑留 11-15 填充 |
| 工具契约 | 单一来源为 `agent/tools/tools.json`（11 个工具 name/description/parameters JSON Schema + 对应 Java 端点路径），Python 启动时读此文件注册为 LangChain tools，两端不各自维护第二份 schema |
| Java 工具路由 | 泛化路由 `POST /api/agent/tools/{toolName}`，Body `{"arguments": {...}}` 原样透传；过滤器验 X-Agent-Secret + 注入 X-Patient-Id，Dispatcher 按 toolName 分发；09 阶段 handler 返回 501，11-15 逐个实现 |

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
| 防刷 key | `reg_ratelimit:{operatorId}:{visitorId}:{scheduleId}` TTL 5s，按操作人+就诊人维度 |
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

### SSE 事件协议（ticket 09 定稿）

| event | data | 说明 |
|-------|------|------|
| delta | `{"text": "..."}` | AI 回复增量文本，逐字渲染 |
| tool_call | `{"tool": "...", "label": "..."}` | 工具调用轨迹，前端展示灰色提示条 |
| card | `{"type": "...", "title": "...", "action": "...", "payload": {...}}` | 确认卡片；action 为 Java C 端接口完整路径，前端点确认直调 Java（不经 Python） |
| done | `{}` | 流结束 |
| error | `{"message": "..."}` | 错误事件 |

| 决策项 | 结论 |
|--------|------|
| 事件生产者 | 仅 Python（唯一生产者），Java 网关不解析不重组，字节级透传 |
| 扩展方式 | 后续 ticket 加新事件类型只改 Python + 前端，不动 Java |
| 请求体 | `POST /api/c/chat/stream`，Body `{"messages": [{"role": "user"\|"assistant", "content": "..."}]}` 全量历史，无状态；前端本地缓存拼装，ticket 10 做存储后请求体不变 |
| 患者身份 | 前端不传 patientId，Java 从 JWT 解析注入 `X-Patient-Id`（身份信息由 Java 注入的可信链） |
| Java 侧失败语义 | Java 转发失败（Python 未启动/超时）返回 HTTP 502，Java 不发 error 事件（协议纯净）；前端对非 200 显示兜底文案 |
| 长上下文 | token 超限截断是 Python 内部策略（保留最近 N 条），不进契约 |
| Python 工程 | HTTP 框架 FastAPI + uvicorn（StreamingResponse 出 SSE）；依赖管理 pip + venv + requirements.txt（dev 加 pytest）；pytest 三类骨架测试：工具契约加载（11 个 schema 合法）/ 意图路由单测（mock LLM）/ SSE 输出格式 |
| 验证策略 | `AGENT_ECHO_MODE`（默认 false）：true 时绕过 LLM 直接回显 delta 事件，用于无 API key 的链路 smoke；false 时真实 LLM 意图分类 + 生成 |
| 转发超时 | Java 首 token 等待 60s（连接/首包），首包后逐块透传不再超时；不做心跳，前端 30s 无事件自行提示断开 |

## 9. 开发模式

| 决策项 | 结论 |
|--------|------|
| 团队 | 多人分工（后端/B端/C端/Agent 并行） |
| 协作方式 | 接口契约先行，各端基于契约并行开发 |
| 总工期 | 2 周（10 个工作日） |

## 10. 问诊与处方（医生工作台）

| 决策项 | 结论 |
|--------|------|
| 问诊创建时机 | 确认挂号（05 confirm）同事务自动创建 consultation（WAITING，pre_diagnosis=null），见 ADR-0011 |
| 问诊状态机 | WAITING -> IN_PROGRESS -> COMPLETED 单向不可回退；COMPLETED 同步 registration 翻 VISITED |
| no-show 处理 | RegistrationScheduler 把过班次 registration 翻 VISITED 时不碰 consultation，留 WAITING（不闭环 no-show） |
| 流转权限 | 仅 consultation.doctor_id = 当前医生，跨医生 403；ADMIN 不参与问诊流转 |
| 预问诊摘要 | 06 只读 pre_diagnosis（null 时前端占位），写入留给 Agent ticket 13 |
| 诊断保存 | 独立接口 PATCH /consultations/{id}/diagnosis，IN_PROGRESS 可随时改；complete 不强制诊断 |
| 问诊消息 | 医生侧仅写 DOCTOR 消息、读全部；仅 IN_PROGRESS 可发消息 |
| 开方时机 | 仅 consultation IN_PROGRESS/COMPLETED 可开方（WAITING 不可）；一问诊允许多处方 |
| 处方生效 | 保存即 ACTIVE，无药师审核，无撤销接口（REVOKED 态留待后续） |
| 禁忌检测 | 开方 POST 同步查 Neo4j：过敏冲突（CONTAINS/CONTRAINDICATED_IN 比对就诊人过敏史文本子串）+ 药物相互作用（INTERACTS_WITH），见 ADR-0012 |
| 过敏史取值 | family_member_id 非空取 patient_family_member.allergy_history，否则取 patient.allergy_history；NULL/空视为无过敏 |
| 禁忌降级 | Neo4j 查询异常返回空 warnings + ERROR 日志，不阻断开方 |
| 冲突处理 | 检测有冲突不阻断保存，响应带 warnings；force=true 仅审计标记，不影响保存 |
| 病历聚合 | 以实际就诊人为中心（family_member_id 口径同 ADR-0010），跨医生可见，不按医生隔离 |
| 处方模板 | 医生个人模板，content JSONB 与开方 items 同构，开方时前端预填来源，后端不感知 |
| Neo4j 访问 | 直接 Cypher（Neo4jClient.query），不建 SDN 实体/repository，收 knowledge 包 |
| 待接诊列表 | 分页 PageResponse，按班次+reg_no 排序，摘要截取前 80 字 |

---

## 术语表（Glossary）

| 术语 | 定义 |
|------|------|
| 号源 | 某个排班时段可被预约的名额数量 |
| 排班（Schedule） | 医生在某天某时段出诊的安排，包含号源总数 |
| 草稿（Draft） | 两段式写操作的第一阶段产物，未真正写库，30分钟过期 |
| 预问诊 | AI 在医生接诊前收集并整理的病情摘要 |
| 工具（Tool） | Agent 可调用的 Java API，用于完成业务操作 |
| 意图（Intent） | 用户消息被路由到的对话能力域，6 类：triage / registration / consultation / pharmacy / reminder / general；emotion 不构成意图，是旁路语气能力 |
| 确认卡片（Card） | SSE `card` 事件嵌入对话流的结构化确认 UI；action 为 Java C 端接口完整路径，payload 为 Java 草稿响应的权威 JSON 原样透传，用户点击后前端直调 Java |
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
| 问诊（Consultation） | 医生与患者的一次诊疗过程，确认挂号时自动创建（WAITING），经 IN_PROGRESS 到 COMPLETED 单向流转，关联 registration_id |
| 问诊状态机 | WAITING（待接诊）-> IN_PROGRESS（接诊中）-> COMPLETED（已完成），单向不可回退；COMPLETED 时同步 registration 翻 VISITED |
| 预问诊摘要（pre_diagnosis） | AI 在医生接诊前生成的病情摘要，由 Agent 写入 consultation.pre_diagnosis（06 阶段为空，13 填充），标注"AI 仅供参考" |
| 诊断（diagnosis） | 医生在问诊过程中填写的诊断结论，存 consultation.diagnosis（问诊级），与处方级诊断独立 |
| 问诊消息（Consultation Message） | 问诊图文对话记录，sender_type 分 DOCTOR/PATIENT；医生侧仅写 DOCTOR 消息，读全部；仅 IN_PROGRESS 可发 |
| 禁忌检测（Contraindication Check） | 开方时查 Neo4j 检测处方药的过敏冲突（CONTAINS/CONTRAINDICATED_IN -> Allergen 比对就诊人过敏史）与药物相互作用（INTERACTS_WITH），不阻断保存，返回 warnings |
| 过敏冲突 | 处方药品含的过敏原与实际就诊人过敏史文本子串匹配命中，禁忌检测结果之一 |
| 强制开方（force） | 医生确认知晓禁忌 warnings 后强制保存处方，force=true 仅作审计标记，不影响保存行为 |
| 处方（Prescription） | 医生开具的用药凭证，关联 consultation_id，保存即 ACTIVE（无需药师审核），含诊断+医嘱+明细项 |
| 处方明细（Prescription Item） | 处方中的单条药品+用法用量记录（drug_id/usage_method/dosage/frequency/remark） |
| 处方模板（Prescription Template） | 医生个人复用的药+用法集合，content JSONB 与开方 items 同构，开方时预填来源，后端不感知 |
| 病历（Medical Record） | 以实际就诊人为中心聚合的历史挂号+问诊+处方+过敏史，不按医生隔离，跨医生可见 |
