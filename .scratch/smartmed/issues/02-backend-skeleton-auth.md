# 02 - 后端骨架与鉴权

**What to build:** Spring Boot 项目能跑通，B 端管理员能用账号密码登录获取 JWT，C 端患者打开即自动登录获取 JWT，未登录请求被拦截返回 401，角色不对返回 403。

**Blocked by:** 01 - 基础设施搭建

**Status:** done

## 实现约束（grill-with-docs 会话确认，见 ADR-0003）

以下决策已与需求方确认，实现时严格遵循，不得自行发挥：

### 鉴权模型（见 ADR-0003）
- **单种 JWT + `typ` 声明**：B 端 `typ=B`、C 端 `typ=C`，共用一个签名密钥，靠路径前缀分权。不拆双 `SecurityFilterChain`。
- **token 载荷**：
  - B 端：`{ sub(sys_user.id), typ:B, role, username, doctor_id?(DOCTOR 才有), iat, exp }`，exp = 12h
  - C 端：`{ sub(patient.id), typ:C, iat, exp }`，exp = 7d
  - `doctor_id` 写进 token，医生端接口直接读 claim，不每请求查库。
  - `username` 写进 B 端 token：供 `/api/b/auth/me` 零 DB 回读用户名渲染顶栏（ADR-0003 token 自包含原则）。
- **均无 refresh token**：过期重新登录，C 端前端静默重调 demo-login。
- **选型**：jjwt（io.jsonwebtoken）+ HS256；密码校验用 Spring Security 自带 `BCryptPasswordEncoder`（对齐 01 种子数据 `$2b$10$...` 哈希）。
- **过滤器链**：单条 `SecurityFilterChain`，路径分权：
  - `/api/auth/**`、`/api/health` 公开
  - `/api/b/**` 需 `typ=B` + role∈{ADMIN,DOCTOR}
  - `/api/c/**` 需 `typ=C`
  - `/api/agent/tools/**` 在 02 阶段**直接 401**（不带 `X-Agent-Secret` 一律拒），09+ 再加 secret 校验逻辑
  - 其他路径拒绝（401）

### 响应与异常
- **HTTP status line 恒 200**：业务错误（含 401/403）靠响应体 `code` 字段区分数值语义。CONTEXT 所述"code 取值为 HTTP 状态码"指数值语义，非要求传输层 status 跟随。
- **统一响应** `Result<T>`：`{ code, message, data }`，静态工厂 `success(data)`(code=200) / `error(code, msg)`。不强求 `ResultCode` 枚举（限制 `BusinessException` 传自定义 code）。
- **异常映射**：
  - 参数校验（`MethodArgumentNotValidException`/`ConstraintViolationException`）→ 400 + 字段级明细（如 `"username: 不能为空"`）
  - `BusinessException` 默认 400，允许传自定义 code 覆盖（如挂号冲突 409，留给后续 ticket）
  - 未登录/token 缺失 → `JwtAuthFilter` 直接写 JSON 响应，code=401，message="未登录或登录已过期"
  - 越权 → `AccessDeniedException` 被全局处理器捕获，code=403，message="无权访问"
  - 登录失败 → code=401，message="用户名或密码错误"（不区分用户名错/密码错，防枚举）
  - 其他未捕获 `Exception` → code=500，message="服务器内部错误"（不泄露堆栈）

### 接口契约（02 仅交付 4 个，严格不碰业务）
| 方法 | 路径 | 鉴权 | 请求 | 响应 data |
|------|------|------|------|-----------|
| GET | `/api/health` | 公开 | - | `{ "status": "UP", "timestamp": "..." }` |
| POST | `/api/auth/login` | 公开 | `{ "username": "admin", "password": "admin123" }` | `{ "token": "...", "role": "ADMIN", "expiresIn": 43200 }`（DOCTOR 才有 `doctorId`，ADMIN 省略该字段） |
| POST | `/api/c/auth/demo-login` | 公开 | 无 body（或空 `{}`） | `{ "token": "...", "patientId": 1, "patientName": "演示患者", "expiresIn": 604800 }` |
| GET | `/api/b/auth/me` | B 端 | - | `{ "userId": 1, "username": "admin", "role": "ADMIN" }`（DOCTOR 才有 `doctorId`，ADMIN 省略该字段） |

- `expiresIn` 单位为**秒**（43200=12h，604800=7d），对齐 jjwt 的 `exp` 秒制。
- **null 字段省略**：响应 DTO 标注 `@JsonInclude(NON_NULL)`，`doctorId` 在 ADMIN 时不出现在 JSON 中（非 `null` 占位），前端用 `hasOwnProperty` 判定。
- demo-login 固定按 `patient.id=1` 签发，查一次库返回 `patientName`，省前端二次查询。
- `/api/health` 只返进程存活（status + timestamp），不检中间件连通性，深度健康检查留作 P2。
- `/api/b/auth/me` 从 JWT claim 解析，零 DB 命中。C 端不需要 `/me`（demo-login 响应已带 patientName，档案接口归 07）。

### 包结构（基包 `com.smartmed.backend`）
```
com.smartmed.backend
├── config/      # SecurityConfig, JwtConfig, MyBatisPlusConfig, CorsConfig, WebConfig
├── common/      # Result<T>, BusinessException
├── security/    # JwtAuthFilter, JwtTokenProvider, UserPrincipal, SecurityUtil
├── auth/        # controller/AuthController, service/AuthService, dto/LoginRequest/LoginResponse
└── health/      # controller/HealthController
```
- **02 不建 `BaseEntity`**（created_at/updated_at 自动填充）：02 无业务表操作，建了是空架子；03 碰 `department`/`doctor` 时再引入 `BaseEntity` + `MetaObjectHandler`。02 只配好 `MyBatisPlusConfig`（分页插件等）。

### 测试（见 spec 000 Testing Decisions）
- **【实现偏差】直连 VM 真实 PostgreSQL，非 Testcontainers**：实现时本机无 Docker，Testcontainers 无法运行。改为集成测试直连 VM（192.168.100.128）上 01 已建好的真实 PG（含 pgvector 扩展 + 完整 schema + 种子数据）。此方案优于原计划：无 schema 脱节问题（H2 不支持 vector），测试只读不污染数据，6 个场景全部通过。代价：测试依赖 VM 可达，CI 环境需保证网络连通。pom 保留 H2 test 依赖备用于非 DB 单元测试。
- **6 个集成测试**（MockMvc 穿透 Controller → Service → DB）：
  1. B 端登录成功：返回 token + role + expiresIn=43200
  2. B 端登录密码错：code=401，message="用户名或密码错误"
  3. 无 token 访问 `/api/b/auth/me`：code=401
  4. C 端 token 访问 `/api/b/**`：code=403（typ 不匹配）
  5. 健康检查：公开访问，status=UP
  6. demo-login：返回 token + patientName="演示患者" + patientId=1
- **不测边界**：过期 token（需 mock 时钟）、伪造签名（jjwt 内部已保证）留作回归，不在 02 范围。

### 跨 ticket 耦合（来自 01）
- 01 种子数据已存 `sys_user` 的 BCrypt 哈希（`$2b$10$...`）和 `patient.id=1` 演示患者，02 必须配 `BCryptPasswordEncoder` Bean 才能匹配此哈希。
- `backend/.env.example` 已由 01 建好，变量名（`DB_HOST`/`JWT_SECRET`/`AGENT_SECRET`/`SERVER_PORT` 等）以该文件为准，`application.yml` 用 `${VAR}` 引用。

## Checklist

- [x] Spring Boot 3.x 项目初始化（Maven），集成 MyBatis-Plus、Spring Security、jjwt
- [x] 统一响应格式 `Result<T>`（静态工厂），全局异常处理器（按上方映射表）
- [x] B 端登录接口 `POST /api/auth/login`（账号密码 -> JWT，typ=B，exp=12h）
- [x] C 端演示登录接口 `POST /api/c/auth/demo-login`（无参 -> 预设 patient.id=1 JWT，typ=C，exp=7d）
- [x] JWT 过滤器 `JwtAuthFilter`：验证 token、注入用户上下文、过期/缺失直接写 401 JSON
- [x] 单条 `SecurityFilterChain`：`/api/b/**` 需 typ=B+角色，`/api/c/**` 需 typ=C，`/api/agent/tools/**` 直接 401
- [x] 当前用户接口 `GET /api/b/auth/me`（从 claim 解析，零 DB）
- [x] 健康检查接口 `GET /api/health`（只返 status+timestamp）
- [x] `application.yml` 配置 PostgreSQL、Redis、Neo4j 连接（`${VAR}` 引用 backend/.env.example 变量）
- [x] `BCryptPasswordEncoder` Bean（匹配 01 种子哈希）
- [x] 包结构 `com.smartmed.backend`（config/common/security/auth/health）+ `MyBatisPlusConfig`
- [x] 6 个集成测试（直连 VM 真实 PG，非 Testcontainers —— 见测试偏差说明）
