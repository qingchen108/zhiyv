# ADR-0018 集成测试时间窗口修复策略

**日期**: 2026-08-03（初版）/ 2026-08-04（修订：改用 Clock 注入）
**状态**: 已确认（方案已修订）
**关联 ticket**: 08b（记录/工作台集成测试存在晚间时间窗口缺陷）

## 背景

`RecordsIntegrationTest` 与 `DoctorWorkspaceIntegrationTest` 在每天 21:00 之后运行必然失败（约 20 个用例）。

根因：`todayPeriod()` 在 17:00 后返回 `EVENING`，而 `TimePeriod.EVENING` 的结束时间是 21:00。21:00 之后创建的"今日排班"立即被判"该班次已结束，无法挂号"（`RegistrationService.validateNotExpired`），所有依赖"创建今日排班 -> 挂号"的用例全部 400。

此外，12:00 和 17:00 边界存在竞态窗口（测试从选择班次到校验之间存在时间漂移风险）。

## 决策

### 初版方案 B（JdbcTemplate 直插）-- 经验证有缺陷，已废弃

初版拟定方案 B：测试改用 `JdbcTemplate` 直接 INSERT `schedule` 表 + Redis SET key，默认 `date=today`、`period=MORNING`，消除对 `POST /api/b/schedules` API 的依赖。

**缺陷**：直插 DB 绕过的只是"创建排班"侧，绕不过"挂号草稿"侧的 `RegistrationService.validateNotExpired`。该方法用 `LocalTime.now()` 校验今日班次是否结束，MORNING 的 endTime=12:00，**12:00 后跑测试仍 400**（比 EVENING 的 21:00 拦得更早）。而"今日待接诊"断言（`findTodayWaiting` 按 `schedule_date=today` 过滤）又要求排班日期=今天，不能用明天。方案 B 无法同时满足两端，**验收标准"21:00 后全量通过"不成立**。

### 实际采用方案：java.time.Clock 注入（ADR 原本推迟的方案）

让生产代码的时间来源可注入，测试固定在上午 10:00，彻底脱离真实墙钟：

1. **生产侧**：新增 `ClockConfig` 提供 `@Bean Clock`（`Clock.systemDefaultZone()`，行为与原 `LocalDate.now()` 一致）；4 个业务 Service（RegistrationService / ScheduleService / ConsultationService / OrderService）构造注入 `Clock`，把 `LocalDate.now()` / `LocalTime.now()` / `LocalDateTime.now()` / `System.currentTimeMillis()` 改为 `.now(clock)` / `clock.millis()`。
2. **测试侧**：`IntegrationTestBase` 内嵌 `@TestConfiguration` 提供 `@Bean @Primary Clock fixedClock()`，固定在"运行当天 10:00"+ 系统默认时区。固定 10:00 后：`LocalDate.now(clock)`=运行当天（"今日待接诊"等断言不受影响），`LocalTime.now(clock)`=10:00（MORNING 08:00-12:00 未结束，挂号不被拒）。测试的 `todayPeriod()` 简化为恒返回 `MORNING`。
3. **未接入 Clock 的类**（年龄派生 / AutoFill / JWT / @Scheduled SQL）仍用静态 `now()`，不影响测试通过；`@Scheduled` 用 DB `now()` 判断，不受 Java Clock 影响。

**为何最终采用**：方案 B 的死结（今日班次校验 vs 今日待接诊断言）只有让时间来源可控才能解开。Clock 注入是唯一让测试在任何时间都通过的方案，且生产代码改动可控（4 个 Service，行为零变化）。

### 排除的其他方案

- 方案 A（21:00 后回退用明天日期 + MORNING）：断言调整成本高，边界竞态仍存在，且"今日待接诊"断言要求 schedule_date=today，用明天会查不到
- `ScheduleFixture`（JdbcTemplate 直插 + Redis SET）：Clock 方案下 API 建排班也能过，直插 DB 多余，不再需要

## 代码结构

| 组件 | 职责 | 内容 |
|------|------|------|
| `config/ClockConfig` | 生产时钟 Bean | `@Bean Clock systemDefaultZone()` |
| `IntegrationTestBase` | 测试薄基类 | 公共字段（mockMvc/objectMapper/tokenProvider/redisTemplate）+ helper（cToken/adminToken/doctorToken/loginAs/today/tomorrow）+ 内嵌 `FixedClockConfig`（@Primary 固定 10:00）|
| 4 个测试类 | 继承基类 | `@Import(FixedClockConfig.class)` + 保留各自 `@SpringBootTest`/Neo4j 排除策略 + 特有 helper |

### 生产代码改动范围

仅 4 个 Service 接入 Clock（`@RequiredArgsConstructor` 自动构造注入）：

| 类 | 改动点 | 业务 |
|----|--------|------|
| `RegistrationService` | 5 处 | reg_no 日期、班次过期校验、取消截止校验、草稿 token 时戳 |
| `ScheduleService` | 2 处 | validateDateWindow、copyWeek 窗口上限 |
| `ConsultationService` | 1 处 | todayWaiting 查询参数 |
| `OrderService` | 3 处 + 时区 | 用药提醒时间/日期、草稿 token 时戳；`ZoneOffset.ofHours(8)` 改为 `clock.getZone()`（消除硬编码时区）|

## 验收标准

- [x] 21:00 之后运行全量 `mvn test` 全部通过（2026-08-04 22:54 验证：96 passed, 0 failures）
- [x] 白天运行时原有用例语义不变（今日待接诊/今日挂号仍验今日，Clock 固定时刻不改日期）

## 约束

- 测试保持 `@Transactional` 回滚 + Redis key 手动清理的既有模式
- 生产代码行为零变化（`Clock.systemDefaultZone()` 与原 `LocalDate.now()` 完全一致）
- Clock 注入不覆盖 @Scheduled 任务（它用 DB `now()`）和年龄派生/JWT/AutoFill（不影响测试，控制改动范围）
