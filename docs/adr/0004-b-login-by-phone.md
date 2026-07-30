# 0004 - 登录方式：手机号+密码（非用户名）

B 端登录改用 `sys_user.phone`（NOT NULL UNIQUE）作为登录键，配合密码校验。`username` 从登录键降级为展示标签：ADMIN 固定字符串"管理员"，DOCTOR 取医生姓名（允许重复、允许 NULL，去掉原 UNIQUE 约束）。登录请求 `POST /api/auth/login` 的 body 从 `{ username, password }` 改为 `{ phone, password }`，`AuthService.login` 改按 `phone` 查 `sys_user`。token claim 仍带 `username`（展示用，供 `/api/b/auth/me` 零 DB 回读顶栏）与 `role`、`doctor_id`（DOCTOR 才有），与 ADR-0003 一致。C 端 demo-login 不受影响（仍按 patient.id=1 签发）。此变更由 03 ticket 发起：医生表原本缺手机号，需求方明确"登录用手机号不用用户名"，故登录键随之迁移。

**Considered Options**: 保留 username 登录（否决，需求方明确要求手机号登录）、双登录键 username+phone 并存（否决，登录入口二义性，且 username 语义已降级为展示标签）、phone 存 doctor 表而非 sys_user（否决，登录是 sys_user 职责，phone 作登录键必须落在 sys_user 上，doctor 表走单源镜像 JOIN 取，见 ADR-0005）。

**Consequences**: 02 已交付的 `LoginRequest`/`AuthService.login`/种子数据须返工（折进 03 交付，不另开 fix ticket）：`sys_user` 加 `phone` 列（NOT NULL UNIQUE），admin/doctor 种子补手机号；`username` 去 UNIQUE、允许 NULL；`LoginRequest` 字段改名；`AuthService.login` 查询条件改 `phone`。`username` 重复不再报错（两个"张三"医生可共存）。登录失败仍统一返回"用户名或密码错误"防枚举（措辞保留但实际校验的是 phone）。后续若有其他登录场景（如 C 端患者手机号登录），需另行决策，本 ADR 仅覆盖 B 端。
