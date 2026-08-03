# 06 - B 端医生工作台

**What to build:** 医生登录后看到今日待接诊患者列表（含 AI 预问诊摘要），点击进入问诊详情，能与患者图文对话，能开具处方（自动检测药物禁忌和过敏风险），能查看患者历史病历，能保存和使用处方模板。

**Blocked by:** 03 - 科室/医生/药品管理, 05 - 挂号全链路

**Status:** done

## 实现约束（grill-with-docs 会话确认，见 ADR-0011、ADR-0012、CONTEXT §10）

以下决策已与需求方确认，实现时严格遵循，不得自行发挥：

### 问诊创建与状态机（ADR-0011）

- **确认挂号时自动创建 consultation**：改 05 的 `RegistrationService.confirm()`，在 `registrationMapper.insert(reg)` 后同事务插入 consultation 行（status=WAITING, pre_diagnosis=null, registration_id/patient_id/doctor_id 冗余写入）。不动 05 的 Lua 扣减/防刷/草稿消费/取消既有逻辑；插入失败整事务回滚走 05 原补偿。
- **状态机**：WAITING -> IN_PROGRESS -> COMPLETED 单向不可回退。COMPLETED 时同步把 registration 翻 VISITED。
- **no-show 不闭环**：`RegistrationScheduler` 把过班次 registration 翻 VISITED 时**不碰** consultation，留 WAITING（待接诊列表过滤 WAITING 自然不显示过班次）。
- **流转权限**：仅 `consultation.doctor_id = 当前医生`，跨医生 403；ADMIN 不参与问诊流转。
- **取消挂号不删 consultation**：挂号取消 != 问诊取消，consultation 留 WAITING 脏数据。

### 诊断与预问诊摘要

- **预问诊摘要**：06 只读 `consultation.pre_diagnosis`（当前 null，前端占位"暂无预问诊摘要"），写入留给 Agent ticket 13。
- **诊断保存**：独立接口 `PATCH /api/b/consultations/{id}/diagnosis`（body `{diagnosis}`），IN_PROGRESS 时可随时改；complete 不强制诊断。
- 诊断分两级：`consultation.diagnosis`（问诊级）与 `prescription.diagnosis`（处方级），独立不同步。

### 问诊消息

- 医生侧**仅写 DOCTOR 消息**、读全部（DOCTOR+PATIENT）；PATIENT 消息由 C 端（08/10）写入。
- **仅 IN_PROGRESS 可发消息**（WAITING 未接诊、COMPLETED 已结束不发）。

### 开方

- **开方时机**：仅 consultation IN_PROGRESS/COMPLETED 可开方（WAITING 不可，400）；一问诊允许多处方。
- **保存即 ACTIVE**，无药师审核，无撤销接口（REVOKED 态留待后续 ticket 14+）。
- **开方请求体**：
  ```json
  {
    "consultationId": 1, "diagnosis": "...", "advice": "...", "force": false,
    "items": [{"drugId":1,"usageMethod":"口服","dosage":"0.3g","frequency":"每日2次","remark":"饭后"}]
  }
  ```
  - `items` 至少 1 条（非空校验）；`diagnosis` 选填；`force` 默认 false。
  - 响应：`{ prescriptionVO, warnings: [...] }`，warnings 含类型（ALLERGY/INTERACTION）+ 药品名 + Allergen/冲突药名 + 说明。

### 禁忌检测（ADR-0012）

- **检测内容**：① 过敏冲突（`Drug -[:CONTAINS|CONTRAINDICATED_IN]-> Allergen`，比对就诊人过敏史文本子串）② 药物相互作用（处方内药品两两 `INTERACTS_WITH`）。**不做**疾病-药品禁忌。
- **过敏史取值**：family_member_id 非空取 `patient_family_member.allergy_history`，否则取 `patient.allergy_history`；NULL/空视为无过敏（跳过过敏比对，仅做相互作用）。口径同 ADR-0010。
- **匹配方式**：取 Neo4j 所有 Allergen 节点名，对过敏史 TEXT 做子串匹配（`allergyHistory.contains(allergenName)`）。
- **何时检测**：开方 `POST` 同步检测返回 warnings，不做单独预检接口。
- **冲突处理**：有冲突**不阻断**保存（仍 ACTIVE）；`force=true` 仅审计标记（记录"医生确认知晓 N 条冲突强制开方"），不影响保存行为。前端用 warnings 弹窗 + force 二次提交做交互确认。
- **Neo4j 降级**：查询异常 catch -> 返回空 warnings + ERROR 日志，不阻断开方。
- **Neo4j 访问方式**：直接 Cypher（`Neo4jClient.query()`），不建 SDN `@Node` 实体/repository。用 `drug.name`（中文名）MATCH Drug 节点，不通过 id 跨库映射。

### 病历聚合

- **以实际就诊人为中心**：family_member_id 非空按该家庭成员聚合，否则按 patient 本人（口径同 ADR-0010）。
- **聚合接口** `GET /api/b/consultations/{id}/medical-record` 返回：基本信息（姓名/性别/年龄/过敏史）+ 历史挂号 + 历史问诊（含诊断）+ 历史处方（含 items）。
- **实际就诊人查询**：family_member_id 非空 `WHERE family_member_id=?`，为空 `WHERE patient_id=? AND family_member_id IS NULL`（与 05 重复挂号校验同一口径）。
- **跨医生可见**：病历不按医生隔离，医生可见患者完整历史含其他医生接诊记录。仅"操作问诊流转"限本人，"看"不限。

### 处方模板

- **归属**：`doctor_id` 限本人，仅当前医生 CRUD 自己的模板，跨医生 403，ADMIN 不管模板。
- **content JSONB 结构**（与开方 items 同构，前端"使用模板"直接预填，无转换层）：
  ```json
  {"items":[{"drugId":1,"usageMethod":"口服","dosage":"0.3g","frequency":"每日2次","remark":"饭后"}],"advice":"多饮水"}
  ```
  不存 consultationId/diagnosis/force（单次开方专属）。
- **开方交互**：开方页"选择模板"下拉，选中前端填充药品行（draft 可再编辑），提交仍走正常 `POST /prescriptions`，后端不感知"来自模板"。
- **CRUD 完整性**：列表（分页）、新建、编辑、删除。不含"另存为模板"（YAGNI）。

### 待接诊列表

- **分页**：`PageResponse<ConsultationVO>`，沿用 `pageNum/pageSize`（默认 1/10，上限 100）。
- **"今日"定义**：registration JOIN schedule WHERE `schedule.doctor_id=当前医生` AND `schedule.schedule_date=CURRENT_DATE`，按排班日期非 consultation.createdAt。
- **每行字段**：consultationId, regNo, scheduleDate, timePeriod, 就诊人姓名/性别/年龄, preDiagnosis（null）, preDiagnosisBrief（截前 80 字，null 时前端显示"暂无"）, consultationStatus。
- **排序**：按 `schedule.time_period`（MORNING/AFTERNOON/EVENING）再按 `reg_no` ASC，不暴露排序参数。

### API 路径与权限矩阵（全部 `@PreAuthorize("hasRole('DOCTOR')")`，ADMIN 不参与）

**问诊 `/api/b/consultations`：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/b/consultations/today` | 今日待接诊列表（status=WAITING，分页） |
| GET | `/api/b/consultations/{id}` | 问诊详情 |
| PATCH | `/api/b/consultations/{id}/start` | 接诊 WAITING->IN_PROGRESS |
| PATCH | `/api/b/consultations/{id}/complete` | 完成 IN_PROGRESS->COMPLETED（同步 registration VISITED） |
| PATCH | `/api/b/consultations/{id}/diagnosis` | 保存诊断（body `{diagnosis}`，IN_PROGRESS 可改） |
| GET | `/api/b/consultations/{id}/messages` | 消息列表（DOCTOR+PATIENT） |
| POST | `/api/b/consultations/{id}/messages` | 发消息（仅 DOCTOR，仅 IN_PROGRESS） |
| GET | `/api/b/consultations/{id}/medical-record` | 患者病历聚合 |
| GET | `/api/b/consultations/{id}/prescriptions` | 该问诊的处方列表 |

**处方 `/api/b/prescriptions`：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/b/prescriptions` | 开方（含禁忌检测，返回 warnings） |
| GET | `/api/b/prescriptions/{id}` | 处方详情（含 items） |

**模板 `/api/b/prescription-templates`：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/b/prescription-templates` | 本人模板列表（分页） |
| POST | `/api/b/prescription-templates` | 新建 |
| PUT | `/api/b/prescription-templates/{id}` | 编辑 |
| DELETE | `/api/b/prescription-templates/{id}` | 删除 |

> 所有涉及具体 consultation/template 的操作校验 `doctor_id = 当前医生`，跨医生 403。doctor_id 从 `SecurityUtil.current().getDoctorId()` 取，不信任 URL/请求体。

### 后端包结构（基包 `com.smartmed.backend`，03/05 模式扩展）

```
com.smartmed.backend
├── consultation/  # controller, service, mapper, entity, dto
│   └── entity/    Consultation, ConsultationMessage
├── prescription/  # controller, service, mapper, entity, dto
│   └── entity/    Prescription, PrescriptionItem, PrescriptionTemplate
└── knowledge/     # Neo4j 禁忌检测
    └── Neo4jContraindicationService, dto/ContraindicationWarning
```

病历聚合逻辑放 `consultation` 的 service（不单独建 medicalrecord 包）。

### B 端前端（Umi Max，03 模式扩展）

- **新增路由**（`.umirc.ts`）：
  - `/workspace` - 待接诊列表页（`access: 'isDoctor'`）
  - `/workspace/:consultationId` - 问诊详情页（`access: 'isDoctor'`）
  - `/prescription-templates` - 处方模板管理页（`access: 'isDoctor'`）
- **菜单分流**：科室/医生/药品/排班管理 `access: 'isAdmin'`（DOCTOR 不可见）；工作台/模板 `access: 'isDoctor'`（ADMIN 不可见）；修改密码 `access: 'loggedIn'`。
- **首页重定向**：`/` 按角色 redirect--ADMIN -> `/department`，DOCTOR -> `/workspace`。
- **问诊详情页布局**（左右分栏）：
  - 左侧：患者信息卡 + 预问诊摘要 + 病历查看按钮（弹窗 Modal）
  - 右侧上半：对话区（消息列表 + 输入框）
  - 右侧下半：诊断输入 + 开方区（药品行增删 + 用模板按钮 + 禁忌警告区 + 开方提交）
- **病历查看**：Modal 弹窗，不单独路由。
- `src/app.ts` 的 `access()` 已定义 `isAdmin/isDoctor`，直接用。

### 测试（spec Testing Decisions，直连真实 PG + `@Transactional` 回滚）

约 12 个集成测试（MockMvc 穿透 Controller -> Service -> DB）：
1. consultation 自动创建：确认挂号后 consultation 行存在（WAITING）
2. 今日待接诊列表：只返回当前医生今日 WAITING，分页正确
3. 跨医生访问问诊详情：403
4. 接诊流转：WAITING->IN_PROGRESS 成功；complete->COMPLETED；registration 同步 VISITED
5. 状态回退：COMPLETED->start 400；IN_PROGRESS->start 400
6. 发消息：DOCTOR 发成功；WAITING/COMPLETED 时发 400
7. 开方：IN_PROGRESS 时成功，返回 warnings
8. 开方冲突：处方含过敏药 -> warnings 含 ALLERGY；force=true 仍保存 ACTIVE
9. 开方：WAITING 时 400
10. 处方模板 CRUD：本人增删改查；跨医生 403
11. 病历聚合：返回挂号+问诊+处方+过敏史，按实际就诊人
12. Neo4j 检测正常：含相互作用药品 -> warnings 含 INTERACTION

> Neo4j 降级不测（难稳定模拟），靠代码 review 保证。

## Checklist

- [x] 后端：改 05 `RegistrationService.confirm()` 同事务插 consultation（WAITING）
- [x] 后端：问诊状态流转 API（start/complete，COMPLETED 同步 registration VISITED）
- [x] 后端：诊断保存 API（PATCH /diagnosis）
- [x] 后端：问诊对话消息 API（发 DOCTOR/读全部，仅 IN_PROGRESS 可发）
- [x] 后端：今日待接诊列表 API（分页 + 摘要截取 80 字）
- [x] 后端：开方 API（含禁忌检测 + warnings + force 审计）
- [x] 后端：禁忌检测（Neo4j 直查 Cypher，过敏冲突 + 相互作用，降级不阻断）
- [x] 后端：患者病历聚合 API（实际就诊人维度）
- [x] 后端：处方模板 CRUD API（本人，跨医生 403）
- [x] 后端：knowledge 包 + Neo4jContraindicationService
- [x] B 端：路由 + 菜单分流（isDoctor/isAdmin）+ 首页按角色重定向
- [x] B 端：待接诊列表页（表格 + 状态标签 + 摘要预览）
- [x] B 端：问诊详情页（左右分栏：左摘要 + 右对话 + 底部诊断/开方）
- [x] B 端：处方编辑器（药品搜索下拉 + 用法用量 + 模板预填 + 禁忌警告展示 + force 确认）
- [x] B 端：病历查看弹窗
- [x] B 端：处方模板管理页
- [x] 后端：12 个集成测试
