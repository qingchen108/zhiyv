# ADR-0018 集成测试时间窗口修复策略

**日期**: 2026-08-03
**状态**: 已确认
**关联 ticket**: 08b（记录/工作台集成测试存在晚间时间窗口缺陷）

## 背景

`RecordsIntegrationTest` 与 `DoctorWorkspaceIntegrationTest` 在每天 21:00 之后运行必然失败（约 20 个用例）。

根因：`todayPeriod()` 在 17:00 后返回 `EVENING`，而 `TimePeriod.EVENING` 的结束时间是 21:00。21:00 之后创建的"今日排班"立即被判"该班次已结束，无法挂号"（`RegistrationService.validateNotExpired`），所有依赖"创建今日排班 → 挂号"的用例全部 400。

此外，12:00 和 17:00 边界存在竞态窗口（测试从选择班次到校验之间存在时间漂移风险）。

## 决策

### 修复方向：方案 B — 测试夹具 SQL 造排班

测试不再通过 `POST /api/b/schedules` API 创建排班，改用 `JdbcTemplate` 直接 INSERT 到 `schedule` 表，彻底消除测试对当前时间的依赖。

**排除的方案**：
- 方案 A（21:00 后回退用明天日期 + MORNING）：断言调整成本高，边界竞态仍存在
- `java.time.Clock` 注入生产代码：好实践但影响面大，留给后续 ticket 单独处理

### Redis key 手动 SET

Fixture INSERT 排班行后，用 `StringRedisTemplate` 手动写入 `schedule:{id}:remaining_slots` key，确保挂号草稿 Lua 扣减能找到 key（否则返回 -2 = 停诊）。

key 格式 `schedule:{id}:remaining_slots` 与 `RegistrationRedisService` 中的 `SLOT_KEY_PREFIX` + `SLOT_KEY_SUFFIX` 一致。

### 代码结构

| 组件 | 职责 | 内容 |
|------|------|------|
| `IntegrationTestBase` | 薄基类 | `adminToken()`、`loginAs(phone, password)`、`today()` |
| `ScheduleFixture` | 独立组件（`@Autowired` 注入） | `createSchedule(doctorId, deptId, date, period, slots)` — JdbcTemplate INSERT + Redis SET |

两个测试文件中的 `todayPeriod()`、`createTodaySchedule()`、`today()`、`adminToken()` 重复代码全部删除，统一由上述组件提供。

### 生产代码改动

**无**。`RegistrationService.validateNotExpired` 中的 `LocalTime.now()` / `LocalDate.now()` 保持不变。Clock 注入是后续重构项。

### 测试排班参数

Fixture 默认使用 `date = LocalDate.now()`、`period = "MORNING"`，保留"今日"语义，确保"今日待接诊列表"等断言不受影响。

## 验收标准

- [ ] 21:00 之后运行全量 `mvn test` 全部通过
- [ ] 白天运行时原有用例语义不变（今日待接诊/今日挂号仍验今日）

## 约束

- 测试保持 `@Transactional` 回滚 + Redis key 手动清理的既有模式
- Fixture 改动时需同步维护 Redis key 写入和 teardown 清理
