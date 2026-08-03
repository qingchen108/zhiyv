# 08 记录/工作台集成测试存在晚间时间窗口缺陷

**Status**: ready-for-agent
**Labels**: test, time-window
**Blocking**: 无

## 描述

`RecordsIntegrationTest` 与 `DoctorWorkspaceIntegrationTest`（08 遗留）在每天 21:00 之后运行必然失败（共约 20 个用例）。

根因：`todayPeriod()` 在 17:00 后返回 `EVENING`，而 `TimePeriod.EVENING` 的结束时间是 21:00（`schedule/entity/TimePeriod.java`）。21:00 之后创建的"今日排班"立即被判"该班次已结束，无法挂号"（`RegistrationService.validateNotExpired`），所有依赖"创建今日排班 → 挂号"的用例全部 400。

首次复现：2026-08-03 21:17 运行全量 `mvn test`（20:58 窗口内运行时全绿）。

## 修复方向（待定，供实施者选择）

- 方案 A：`todayPeriod()` 在 EVENING 结束（21:00）后回退用明天的日期 + MORNING 班次，并同步调整依赖"今日待接诊"的断言
- 方案 B：测试夹具直接 SQL 造排班（不依赖班次时间），彻底消除时间敏感
- 注意：测试为 `@Transactional` 回滚 + Redis key 手动清理，改动时保持这两点

## 验收标准

- [ ] 21:00 之后运行全量 `mvn test` 全部通过
- [ ] 白天运行时原有用例语义不变（今日待接诊/今日挂号仍验今日）
