# 0013 - B 端 JWT 双 token 与会话管理

B 端（web-admin）鉴权采用 **access(30min) + refresh(8h) 双 token** 设计（ticket 02/06 实现）：

## 决策

- **access token**（typ=B，30min）：无状态 JWT，携带 `role` / `doctor_id` / `must_change_password` / `jti` / `refresh_jti` / `absolute_exp` claims。
- **refresh token**（typ=B_RT，8h）：不存本地，仅以 `rjti` 为 key 存 **Redis 会话**（`userId:role:doctorId:mustChange(1/0):absoluteExp`），随会话 TTL 过期。
- **实时吊销**：每个受保护请求在 `JwtAuthFilter` 校验会话（`isSessionActive`），logout / 改密 / 重用检测后对应 access token 即刻失效（而非等 30min 自然过期）。
- **轮换 + 重用检测**：refresh 换新即轮换 rjti；旧 rjti 复用视为盗用，吊销该用户全部会话。
- **固定窗口**：`absolute_exp` 为登录时刻 + 8h，窗口内任意刷新不延长绝对截止。
- **Redis 不可用 fail-open**：`isSessionActive` 在 Redis 异常时放行，避免基础设施故障阻断现有会话。
- C 端（小程序）仍为单 JWT（typ=C，7 天），与 B 端分离（ADR-0003 typ claim 体系）。

## Considered Options

- **纯无状态双 token（否决）**：无法实时吊销，logout/改密后旧 token 仍有效，与改密会话吊销（ADR-0005）冲突。
- **refresh 存 DB（否决）**：演示系统无多实例需求，Redis 足够且天然带 TTL。
- **C 端也做双 token（否决）**：演示登录 + 7 天固定 token 满足需求，不做多余复杂度。

## Consequences

- `JwtTokenProvider` 配置 `bAccessExpireSeconds=1800`、`bRefreshExpireSeconds=28800`；`LoginResponse.expiresIn=1800`。
- 每次请求多一次 Redis 读（key 命中即可），性能可接受；Redis 不可用时全部放行（安全权衡：演示环境可接受）。
- 会话集合 `userSessions:{userId}` 支持一键吊销全部会话（改密/盗用检测）。
