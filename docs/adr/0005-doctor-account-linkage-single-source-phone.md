# 0005 - 医生账号联动 + 手机号单源镜像 + 首登改密

doctor 业务实体与 sys_user 登录账号分离（CONTEXT §2），但 03 起"新建医生即开账号"：`POST /api/b/doctors` 同事务写 `doctor` 行 + `sys_user` 行（role=DOCTOR，doctor_id 关联，phone 作登录键，密码 BCrypt("123456")，`must_change_password=true`）。删除 doctor 同事务删其 `sys_user`（避免 doctor_id 野指针）。手机号**单源镜像**：只存 `sys_user.phone`，`doctor` 表不存 phone 列，展示时 JOIN `sys_user` 取（`DoctorVO.phone` 由后端关联查询填充），改手机号只改 `sys_user.phone` 一处。首登强制改密：新建账号 `must_change_password=true`，登录响应与 `/api/b/auth/me` 带该标志，前端拦截跳 `POST /api/b/auth/change-password`（old+new），改密成功后置 false。

**Considered Options**: doctor CRUD 不开账号、账号另开 ticket（否决，需求方选 A"新建医生即开账号"，demo 流程要求新建医生立即可登录）、doctor 表与 sys_user 双字段存 phone（否决，冗余且需双向同步，单源镜像更干净）、phone 存 doctor 表 sys_user 不存（否决，登录是 sys_user 职责，登录键必须在 sys_user）、软禁用 sys_user 而非物理删（否决，doctor 已物理删，留账号致 doctor_id 野指针，与 CONTEXT 物理删除一致选同事务删）、首登改密用独立首次登录页（否决，复用现有登录 + 标志位拦截更轻）。

**Consequences**: doctor CRUD 不再是单表操作，service 层须 `@Transactional` 保证 doctor+sys_user 同写同删。`DoctorVO` 返回 phone 需 JOIN sys_user（MyBatis-Plus 关联查询或 XML），doctor 列表查询多一次关联。`sys_user` 加 `must_change_password` 列（NOT NULL，默认 false，种子账号置 true 触发首登改密）。02 种子 admin/doctor 账号须补 phone + 置 must_change_password=true（折进 03 返工）。改密接口校验旧密码正确性，新密码 BCrypt 哈希后更新。首登未改密前，除 `/api/b/auth/change-password` 与 `/api/b/auth/me` 外的 B 端接口是否拦截——03 实现：不拦截业务接口，仅前端按 mustChangePassword 标志跳改密页（后端不强制造约，简化实现；若需后端强约束另立 ADR）。
