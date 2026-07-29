# 02 — 后端骨架与鉴权

**What to build:** Spring Boot 项目能跑通，B 端管理员能用账号密码登录获取 JWT，C 端患者打开即自动登录获取 JWT，未登录请求被拦截返回 401，角色不对返回 403。

**Blocked by:** 01 — 基础设施搭建

**Status:** ready-for-agent

- [ ] Spring Boot 3.x 项目初始化（Maven），集成 MyBatis-Plus、Spring Security、JWT
- [ ] 统一响应格式 { code, message, data }，全局异常处理器
- [ ] B 端登录接口 POST /api/auth/login（账号密码 → JWT）
- [ ] C 端演示登录接口 POST /api/c/auth/demo-login（无需参数 → 预设患者 JWT）
- [ ] JWT 过滤器：验证 token、注入用户上下文、过期返回 401
- [ ] 权限拦截：/api/b/** 需 ADMIN 或 DOCTOR 角色，/api/c/** 需患者身份
- [ ] 健康检查接口 GET /api/health 返回服务状态
- [ ] application.yml 配置 PostgreSQL、Redis、Neo4j 连接（读取环境变量）
