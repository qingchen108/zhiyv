# 0007 - B 端 JWT 存储：localStorage + Zustand 镜像

web-admin（B 端）登录获取的 JWT 存 `localStorage` 持久化，Zustand auth store 在内存持有用户信息（role/username/doctorId/mustChangePassword）作为镜像。刷新页面时从 localStorage 恢复 token，并调 `/api/b/auth/me` 恢复用户信息到 store。请求层（Umi Max `request`）在 `src/app.ts` 配拦截器：请求注入 `Authorization: Bearer <token>`，响应 code=401 清 token + store 并跳 `/login`。路由守卫用 Umi `access` 插件，定义 `loggedIn`（token 存在）/ `isDoctor`（role=DOCTOR）等 access 函数，路由配置对应 access。

**Considered Options**: memory-only Zustand（否决，刷新页面丢 token 强制重登，B 端管理后台体验差）、httpOnly cookie（否决，后端 ADR-0003 是 JWT 无状态 + `Authorization: Bearer` 头方案，改 cookie 须返工后端鉴权链路，不划算）、sessionStorage（否决，关闭标签页即丢，跨标签页不共享，B 端多标签操作不便）。

**Consequences**: localStorage 可被 XSS 窃取 token--B 端管理后台内容不来自用户输入（科室/医生/药品数据由 ADMIN 录入），XSS 攻击面小，可接受；若后续引入富文本或用户上传内容须重新评估。token 不存 httpOnly cookie 意味着前端 JS 可读 token，便于在拦截器/路由守卫中判断登录态。登出操作须同时清 localStorage 和 Zustand store。Zustand store 初始化时从 localStorage 读 token 决定初始登录态，避免刷新闪现登录页。此方案仅覆盖 B 端，C 端（小程序）token 存储由 07 ticket 决策。
