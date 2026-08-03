# 03 - 科室/医生/药品管理

**What to build:** 管理员登录 B 端后台后，能看到科室、医生、药品的列表页，能新增、编辑、删除，数据实时生效。医生能编辑自己的部分信息。

**Blocked by:** 02 - 后端骨架与鉴权

**Status:** done

## 实现约束（grill-with-docs 会话确认，见 ADR-0004~0007）

以下决策已与需求方确认，实现时严格遵循，不得自行发挥：

### 数据模型变更（schema 返工，折进 03 交付）

#### `sys_user` 表（ADR-0004）
- **新增列 `phone`**：VARCHAR(32) NOT NULL UNIQUE，作登录键。
- **新增列 `must_change_password`**：BOOLEAN NOT NULL DEFAULT false。新建账号置 true，首登改密后置 false。
- **`username` 降级**：去掉 UNIQUE 约束，允许 NULL；语义从登录键变为展示标签（ADMIN 固定"管理员"，DOCTOR 取医生姓名，允许重复）。
- **02 种子返工**：admin/doctor 账号补 phone（如 admin=13800000000、doctor=13800000002），置 `must_change_password=true` 触发首登改密。

#### `doctor` 表（ADR-0005）
- **新增列 `gender`**：VARCHAR(8)，男/女（与 patient.gender 口径一致）。
- **新增列 `birth_date`**：DATE，存出生日期，年龄由后端派生计算（`Period.between(birthDate, now).getYears()`），不存 age 列。
- **不加 phone 列**：手机号单源镜像，只存 sys_user.phone，展示靠 JOIN。
- **02 种子返工**：5 个种子医生补 gender + birth_date（如张呼吸=男/1975-03-10）。phone 不补在 doctor 表，其登录 phone 在 sys_user 返工时对齐（demo 医生 doctor.id=1 对应 sys_user doctor 账号 phone=13800000002）。

### 登录与改密（ADR-0004、ADR-0005，02 返工折进 03）

- **登录改 phone**：`POST /api/auth/login` body 从 `{username,password}` 改为 `{phone,password}`；`AuthService.login` 改按 `phone` 查 `sys_user`。登录失败仍统一"用户名或密码错误"防枚举（措辞保留实际校验 phone）。
- **登录响应加字段**：`{ token, role, expiresIn, doctorId?, mustChangePassword }`。
- **`/me` 加字段**：`GET /api/b/auth/me` 响应加 `mustChangePassword: boolean`。
- **新接口 `POST /api/b/auth/change-password`**：body `{ oldPassword, newPassword }`，校验旧密码、BCrypt 新密码、更新 `must_change_password=false`。首登 `mustChangePassword=true` 时前端拦截跳此接口。后端不强制业务接口拦截（仅前端按标志跳改密页，简化实现）。

### 医生账号联动（ADR-0005）

- **新建 doctor 即开账号**：`POST /api/b/doctors` 同事务写 `doctor` 行 + `sys_user` 行（role=DOCTOR，doctor_id 关联，phone=请求体 phone，密码 BCrypt("123456") 或请求体 password，`must_change_password=true`）。
- **`name` 同步**：doctor.name 同步写 sys_user.username。
- **删除 doctor 同事务删 sys_user**：先删 sys_user（子表无引用），再删 doctor（前置检查通过后）；service 层 `@Transactional`。
- **`DoctorVO` 含 phone**：后端 JOIN sys_user 取 phone 填入 VO；含 `age`（birthDate 派生）；不含密码。

### 删除策略（ADR-0006，三实体统一）

物理删除 + 前置引用检查，有引用则 `BusinessException(409, "...存在...记录，无法删除")` 拒绝：

| 实体 | 前置检查（有引用则拒绝） |
|------|--------------------------|
| department | doctor.department_id、schedule.department_id |
| doctor | schedule.doctor_id、registration.doctor_id、consultation.doctor_id、prescription.doctor_id、prescription_template.doctor_id、medication_reminder.doctor_id |
| drug | drug_pharmacy_stock.drug_id、prescription_item.drug_id、medication_reminder.drug_id |

> 03 阶段仅 schedule（04）、drug_pharmacy_stock（02 种子）有数据，其余表 05+ 才有数据，检查代码照写为后续防护。

### 权限模型

- **科室/药品**：仅 ADMIN。`/api/b/departments/**`、`/api/b/drugs/**` 全部 `@PreAuthorize("hasRole('ADMIN')")`。
- **医生**：ADMIN 全部 + DOCTOR 编辑本人。
  - `PUT /api/b/doctors/{id}` -> `@PreAuthorize("hasRole('ADMIN')")`，全字段。
  - `PUT /api/b/doctors/me` -> `@PreAuthorize("hasRole('DOCTOR')")`，仅收 `{specialty?, avatarUrl?, intro?}`（独立 DTO `DoctorProfileUpdateRequest`），doctor_id 从 `SecurityUtil.current().getDoctorId()` 取，不信任 URL/请求体。
  - DOCTOR 调 `PUT /api/b/doctors/{id}` 直接 403（@PreAuthorize 拦）。
  - DOCTOR 可改字段：`specialty` / `avatar_url` / `intro`（story 36）；不可改 department_id/name/gender/birth_date/title/good_rate/phone。

### department 硬编码 hospital_id（CONTEXT 术语表"唯一医院"）

- 新建 department 时后端写死 `hospital_id=1`，DTO 不含该字段，前端无感知。与 CONTEXT"B 端不提供医院管理页面"一致。

### 分页与列表（三实体全部分页）

- **分页参数**：`pageNum`/`pageSize`（query），默认 1/10，pageSize 上限 100 防拉爆。
- **筛选**：department `name?` 模糊；doctor `departmentId?` + `name?` 模糊；drug `name?` 模糊。
- **排序**：固定 `id ASC`，不暴露排序参数。
- **响应**：`Result<PageResponse<T>>`，`PageResponse<T>{ records, total, page, size }`（仅 4 字段，不暴露 MyBatis-Plus IPage 内部字段）。

### BaseEntity + MetaObjectHandler（兑现 02 留的口子）

- **BaseEntity 只含 `createdAt`/`updatedAt`**：`@TableField(fill=INSERT)` / `@TableField(fill=INSERT_UPDATE)`，无逻辑删除字段。
- **仅 03 新增实体继承**（Department/Doctor/Drug），不改 02 的 SysUser/Patient。
- **MetaObjectHandler Java 层全填**：`insertFill` 填 createdAt+updatedAt，`updateFill` 填 updatedAt；DB `DEFAULT now()` 兜底。

### 接口契约表

#### 科室 `/api/b/departments`（仅 ADMIN）

| 方法 | 路径 | 请求 | 响应 data |
|------|------|------|-----------|
| GET | `/api/b/departments` | query: pageNum?/pageSize?/name? | `PageResponse<DepartmentVO>` |
| GET | `/api/b/departments/{id}` | - | `DepartmentVO` |
| POST | `/api/b/departments` | `{ name, description? }` | `DepartmentVO` |
| PUT | `/api/b/departments/{id}` | `{ name, description? }` | `DepartmentVO` |
| DELETE | `/api/b/departments/{id}` | - | 无 |

#### 医生 `/api/b/doctors`

| 方法 | 路径 | 鉴权 | 请求 | 响应 data |
|------|------|------|------|-----------|
| GET | `/api/b/doctors` | ADMIN\|DOCTOR | query: pageNum?/pageSize?/departmentId?/name? | `PageResponse<DoctorVO>` |
| GET | `/api/b/doctors/{id}` | ADMIN\|DOCTOR | - | `DoctorVO` |
| GET | `/api/b/doctors/me` | DOCTOR | - | `DoctorVO`（本人） |
| POST | `/api/b/doctors` | ADMIN | `{ departmentId, name, gender, birthDate?, title?, specialty?, avatarUrl?, intro?, goodRate?, phone, password? }` | `DoctorVO` |
| PUT | `/api/b/doctors/{id}` | ADMIN | 全字段（除 id/createdAt/updatedAt） | `DoctorVO` |
| PUT | `/api/b/doctors/me` | DOCTOR | `{ specialty?, avatarUrl?, intro? }` | `DoctorVO` |
| DELETE | `/api/b/doctors/{id}` | ADMIN | - | 无（同事务删 sys_user） |

> 新建医生 `phone` 必填，`password` 可选默认 "123456"，建好的 sys_user `must_change_password=true`。

#### 药品 `/api/b/drugs`（仅 ADMIN）

| 方法 | 路径 | 请求 | 响应 data |
|------|------|------|-----------|
| GET | `/api/b/drugs` | query: pageNum?/pageSize?/name? | `PageResponse<DrugVO>` |
| GET | `/api/b/drugs/{id}` | - | `DrugVO` |
| POST | `/api/b/drugs` | `{ name, specification?, manufacturer?, price, dosageForm? }` | `DrugVO` |
| PUT | `/api/b/drugs/{id}` | 全字段（除 id/createdAt/updatedAt） | `DrugVO` |
| DELETE | `/api/b/drugs/{id}` | - | 无 |

#### 02 返工项（折进 03）

| 方法 | 路径 | 请求 | 响应 data |
|------|------|------|-----------|
| POST | `/api/auth/login` | `{ phone, password }`（原 `{username,password}`） | `{ token, role, expiresIn, doctorId?, mustChangePassword }` |
| POST | `/api/b/auth/change-password` | `{ oldPassword, newPassword }` | 无 |
| GET | `/api/b/auth/me` | - | 原响应 + `mustChangePassword: boolean` |

### B 端工程初始化（ADR-0007）

- **Umi 4 Max（`@umijs/max`）**：`npx create-umi@latest` 初始化，约定式路由（`pages/` 目录即路由）。
- **目录结构**：`src/{pages,services,stores,components,utils}` + `.umirc.ts`。
  - `pages/{login,department,doctor,drug}/`
  - `services/` 按实体分文件
  - `stores/` Zustand auth store
  - `utils/` request 封装
- **JWT 存储**：localStorage 持久化 token + Zustand store 镜像用户信息；刷新调 `/api/b/auth/me` 恢复。
- **路由守卫**：Umi `access` 插件，`loggedIn`（token 存在）/ `isDoctor`（role=DOCTOR）预留。
- **请求层**：Umi Max `request`，`src/app.ts` 配拦截器注入 `Authorization: Bearer <token>` + 401 清 token 跳登录。
- **登录页**：手机号+密码表单（非用户名），调 `/api/auth/login`。
- **三个管理页**：科室/医生/药品，表格 + 新增/编辑弹窗 + 删除确认；医生表单含科室下拉、职称选择、gender、birthDate、phone。

### 包结构（基包 `com.smartmed.backend`，02 基础上扩展）

```
com.smartmed.backend
├── common/      # Result<T>, BusinessException, PageResponse<T>（新增）
├── config/      # SecurityConfig, MyBatisPlusConfig, CorsConfig, MetaObjectHandler（新增）
├── security/    # JwtAuthFilter, JwtTokenProvider, UserPrincipal, SecurityUtil, TypAuthorizationManager
├── auth/        # controller/AuthController, service/AuthService, dto/...
├── health/      # controller/HealthController
├── base/        # BaseEntity（新增）
├── department/  # controller/DepartmentController, service/DepartmentService, mapper/DepartmentMapper, entity/Department, dto/...
├── doctor/      # controller/DoctorController, service/DoctorService, mapper/DoctorMapper, entity/Doctor, dto/...
└── drug/        # controller/DrugController, service/DrugService, mapper/DrugMapper, entity/Drug, dto/...
```

### 测试（见 spec 000 Testing Decisions）

- **直连 VM 真实 PG**（02 偏差延续），非 Testcontainers。
- **`@Transactional` 回滚**隔离测试数据（03 有写操作，靠回滚不污染 VM 数据库）。
- **15 个集成测试**（MockMvc 穿透 Controller -> Service -> DB）：
  1. 管理员登录（phone+password）：返回 token + role + expiresIn + mustChangePassword
  2. 登录密码错：code=401 "用户名或密码错误"
  3. 首登改密：`POST /change-password` 成功后 mustChangePassword 变 false
  4. 改密时旧密码错：code=400/401
  5. 新建医生（含 phone）：doctor 行 + sys_user 行同事务写入，sys_user 密码=BCrypt("123456")、mustChangePassword=true
  6. 新建医生 phone 重复：409 拒绝
  7. DOCTOR 调 `PUT /api/b/doctors/{id}`：403
  8. DOCTOR 调 `PUT /api/b/doctors/me` 改 specialty：成功
  9. DOCTOR 调 `PUT /api/b/doctors/me` 试图改 title：忽略（DTO 不含该字段）
  10. 删除 doctor（无引用）：doctor + sys_user 同事务删
  11. 删除 doctor（有 schedule 引用）：409 拒绝
  12. 删除 department（有 doctor 引用）：409 拒绝
  13. 删除 drug（有 drug_pharmacy_stock 引用）：409 拒绝
  14. 分页列表（doctor 按 departmentId 筛选）：PageResponse 结构正确
  15. C 端 token 访问 `/api/b/**`：403（02 回归）
- **前端不写自动化测试**（spec 明确）。

## Checklist

### 02 返工（折进 03）
- [x] schema：`sys_user` 加 `phone`（NOT NULL UNIQUE）、`must_change_password`（BOOLEAN NOT NULL DEFAULT false）；`username` 去 UNIQUE、允许 NULL
- [x] schema：`doctor` 加 `gender`、`birth_date`；不加 phone
- [x] seed：admin/doctor 账号补 phone + must_change_password=true；5 个种子医生补 gender + birth_date
- [x] `LoginRequest` 改 `{phone,password}`；`AuthService.login` 改按 phone 查
- [x] 登录响应 + `/me` 加 `mustChangePassword`
- [x] 新接口 `POST /api/b/auth/change-password`

### 后端 03 新增
- [x] `BaseEntity`（createdAt/updatedAt）+ `MetaObjectHandler`（Java 层全填）
- [x] `PageResponse<T>` 通用分页响应
- [x] 科室 CRUD（`/api/b/departments`，hospital_id 硬编码 1，仅 ADMIN，物理删+前置检查）
- [x] 医生 CRUD（`/api/b/doctors`，新建同事务建 sys_user，删除同事务删 sys_user，DOCTOR `/me` 仅改 specialty/avatar_url/intro）
- [x] 药品 CRUD（`/api/b/drugs`，仅 ADMIN，物理删+前置检查）
- [x] `DoctorVO` JOIN sys_user 取 phone + 派生 age
- [x] 15 个集成测试（`@Transactional` 回滚）

### B 端 03 新增
- [x] Umi 4 Max 项目初始化，集成 Ant Design、Zustand、Less
- [x] 登录页（手机号+密码）+ JWT 存 localStorage + Zustand 镜像 + 路由守卫（access 插件）
- [x] request 拦截器（`src/app.ts`：注入 token + 401 跳登录）
- [x] 科室管理页（表格 + 新增/编辑弹窗 + 删除确认）
- [x] 医生管理页（表格 + 表单含科室下拉、职称、gender、birthDate、phone）
- [x] 药品管理页（表格 + 表单）

### 权限
- [x] 科室/药品仅 ADMIN，医生管理 ADMIN 全部 + DOCTOR 编辑本人
