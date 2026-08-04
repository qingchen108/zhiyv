# 12 — Agent 预约挂号

**What to build:** 导诊完成后，Agent 引导患者选择排班时段，生成挂号确认卡片（草稿），患者点击确认后完成挂号并返回凭证卡片，号源不足时提示重新选择。

**Blocked by:** 11 — Agent 智能导诊, 05 — 挂号全链路

**Status:** done

## 决策（grill-with-docs 2026-08-03）

- Agent `create_registration_draft` handler 直接委托 `RegistrationService.createDraft()`，patientId 从 `X-Patient-Id` header 注入（ADR-0016）
- `query_schedule` 返回扁平列表（schedule_id / doctor_id / doctor_name / department_name / schedule_date / time_period / remaining_slots / status），过滤 SUSPENDED 和余量=0
- 挂号确认卡片 `type=registration_confirm`，`action=/api/c/registrations/confirm`，payload 含 draftKey / confirmToken / scheduleId / doctorName / departmentName / scheduleDate / timePeriod / timeRange / familyMemberId（nullable）
- 不新增家庭成员查询工具，复用 `get_medical_record` 返回家庭成员列表
- confirm 失败不自动回调 Agent，草稿已被 DEL 卡片失效，前端弹 toast，用户自然语言发起新一轮
- 挂号凭证卡片前端本地渲染（confirm API 返回 RegistrationVO），不进 SSE 事件流
- 不实现就诊提醒调度（Out of Scope），Agent 仅文案提示"就诊前 1 天会提醒您"

## 验收标准

- [x] Java 工具实现：query_schedule（按科室/医生/日期查可用排班和余量，返回扁平列表）
- [x] Java 工具实现：create_registration_draft（委托 RegistrationService.createDraft，X-Patient-Id 注入身份）
- [x] Agent 展示排班选项 → 用户选择 → 调用草稿工具 → 生成确认卡片（card 事件）
- [x] 确认卡片内容：type=registration_confirm + payload 含完整草稿信息
- [x] 用户确认 → 前端直调 /api/c/registrations/confirm → 成功前端本地渲染凭证卡片
- [x] 号源刚被抢完 → 前端 toast 提示 → 用户自然语言重新发起 → Agent 重查排班
- [x] 帮家人挂号：Agent 通过 get_medical_record 确认 family_member_id
- [x] Agent 挂号成功文案提示"就诊前 1 天会提醒您"
- [x] pytest 测试：正常挂号流程、号源不足场景、草稿过期场景
