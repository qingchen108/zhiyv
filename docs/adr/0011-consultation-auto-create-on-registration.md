# 0011 - 问诊于确认挂号时自动创建

确认挂号（05 `RegistrationService.confirm()`）成功时，在同一事务内自动插入一行 `consultation`（status=WAITING, pre_diagnosis=null, 冗余 registration_id/patient_id/doctor_id）。问诊状态机 WAITING -> IN_PROGRESS -> COMPLETED 单向不可回退，COMPLETED 时同步把 registration 翻 VISITED。

## Considered Options

- **确认挂号时自动创建（采纳）**：生命周期清晰，`consultation.pre_diagnosis` 有行可写让 Agent（ticket 13）填摘要，待接诊列表查 WAITING 语义自洽。
- **医生打开工作台时懒创建**：列表查 registration，按需建 consultation。复杂且预问诊摘要无落点。
- **医生点"接诊"时才创建**：待接诊列表纯查 registration（无 consultation 行），Agent 无法在接诊前写预问诊摘要，待接诊列表永远无摘要。

## Consequences

- 05 已完成的 `confirm()` 需追加一段 consultation insert，不动 Lua 扣减/防刷/草稿消费既有逻辑；插入失败整事务回滚走 05 原补偿。
- 取消挂号不删 consultation 行（挂号取消 != 问诊取消），留 WAITING 脏数据。
- `RegistrationScheduler` 把过班次 registration 翻 VISITED 时**不碰** consultation，no-show 的 consultation 永远停在 WAITING（demo 不闭环 no-show，待接诊列表过滤 WAITING 自然不显示过班次）。
- 两套状态机并存：registration（REGISTERED/VISITED/CANCELLED）与 consultation（WAITING/IN_PROGRESS/COMPLETED）正交，COMPLETED 是唯一联动点（同步翻 VISITED）。
