# 0003 - 鉴权模型：单种 JWT + typ 声明 + 路径分权

B 端（ADMIN/DOCTOR）与 C 端（patient）共用一个签名密钥的 JWT，靠 `typ` 声明（`B` 或 `C`）区分端别，再由单条 `SecurityFilterChain` 按路径前缀分权（`/api/b/**` 需 `typ=B`+role 非空，`/api/c/**` 需 `typ=C`）。token 自包含：B 端携带 `sub`(sys_user.id)、`role`、`username`（供 `/api/b/auth/me` 零 DB 回读）、`doctor_id`（DOCTOR 才有，ADMIN 省略），C 端携带 `sub`(patient.id)。签名用 jjwt + HS256（对称密钥，Java 自签自验，Python Agent 不验 JWT 走 `X-Agent-Secret`）。过期：B 端 12h、C 端 7d，均无 refresh token。密码校验用 Spring Security 自带 `BCryptPasswordEncoder`（对齐 01 种子数据的 `$2b$10$...` 哈希）。`/api/agent/tools/**` 在 02 阶段直接拒绝，未携带有效凭证返回 401（自定义 AuthenticationEntryPoint 写统一响应体），09+ 再加 `X-Agent-Secret` 校验。

**Considered Options**: 两种 JWT + 双 `SecurityFilterChain`（否决，样板代码多无实质收益）、`aud` 声明区分端别（否决，引入 OAuth2 语义过重）、RS256 非对称签名（否决，无跨服务公钥分发需求，过度设计）、refresh token（否决，违背无状态骨架，demo-login 重登零摩擦）、`doctor_id` 每请求查库（否决，违背 token 自包含原则且医生端流程不需额外 DB 命中）。

**Consequences**: `sys_user.doctor_id` 重分配后 token 过期前是旧值,演示场景可接受。两端过期时间不一致需前端分别处理：B 端 12h 到期需用户重输密码，C 端 7d 到期前端静默重调 demo-login。HTTP 响应 status line 恒 200，业务错误（含 401/403）靠响应体 `code` 字段区分数值语义——CONTEXT 所述"code 取值为 HTTP 状态码"指数值语义而非要求传输层 status 跟随；恒 200 让前端统一在 `.then()` 判 code，契合 10 工作日工期 + 前端无自动化测试的联调节奏。
