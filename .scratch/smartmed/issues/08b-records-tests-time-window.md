# 08 记录/工作台集成测试存在晚间时间窗口缺陷

**Status**: done
**Labels**: test, time-window
**Blocking**: 无
**ADR**: docs/adr/0018-integration-test-time-window-fix.md

## 描述

`RecordsIntegrationTest` 与 `DoctorWorkspaceIntegrationTest`（08 遗留）在每天 21:00 之后运行必然失败（共约 20 个用例）。

根因：`todayPeriod()` 在 17:00 后返回 `EVENING`，而 `TimePeriod.EVENING` 的结束时间是 21:00（`schedule/entity/TimePeriod.java`）。21:00 之后创建的"今日排班"立即被判"该班次已结束，无法挂号"（`RegistrationService.validateNotExpired`），所有依赖"创建今日排班 → 挂号"的用例全部 400。

首次复现：2026-08-03 21:17 运行全量 `mvn test`（20:58 窗口内运行时全绿）。

## 修复方向（已确认 — 方案 B）

**测试夹具 SQL 造排班**，彻底消除时间敏感：

- `ScheduleFixture`（JdbcTemplate INSERT + StringRedisTemplate SET `schedule:{id}:remaining_slots`）替代 `createTodaySchedule()`
- `IntegrationTestBase` 薄基类提供 `adminToken()` / `loginAs()` / `today()`，消除两个测试文件的重复代码
- 排班参数固定 `date=today`、`period=MORNING`，保留今日语义
- 不改动生产代码（Clock 注入留给后续 ticket）

详细决策见 ADR-0018。

## 验收标准

- [ ] 21:00 之后运行全量 `mvn test` 全部通过
- [ ] 白天运行时原有用例语义不变（今日待接诊/今日挂号仍验今日）
