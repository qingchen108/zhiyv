# 0009 - 排班号源管理：枚举班次 + 创建即发布 + Redis 同步策略

排班与号源管理（ticket 04）的核心设计决策：

## 时段模型

采用**预设班次枚举**而非自由时间：`MORNING(08:00-12:00)` / `AFTERNOON(14:00-17:30)` / `EVENING(18:00-21:00)`。新增 `time_period VARCHAR(16)` 列，`start_time`/`end_time` 由后端根据枚举自动填充。冲突校验退化为 UNIQUE(doctor_id, schedule_date, time_period)，无需区间重叠计算。

## 发布与 Redis 同步

创建即发布、即写 Redis（无草稿态）。status 仅 PUBLISHED / SUSPENDED 两态。

- 创建排班 → SET `schedule:{id}:remaining_slots` = remaining_slots
- 停诊（PUBLISHED → SUSPENDED）→ DEL key，C 端查不到即不可挂号
- 恢复（SUSPENDED → PUBLISHED）→ SET key = DB 中的 remaining_slots（不重置）
- 删除排班 → DEL key

Redis 写入失败走 @Async 重试一次 + ERROR 日志，C 端保留 Redis miss 后查 PG 回填的 fallback（见已有容错策略）。

## 删除约束

与 ADR-0006 一致：有挂号引用（registration.schedule_id）→ 409 拒绝，仅可停诊；无引用 → 物理删除 + DEL Redis key。

## 批量复制（周复制）

源周排班复制到目标周（周以**周一**为起点）：total_slots 照抄，remaining_slots = total_slots（全新放号）。目标周已存在的 doctor_id + schedule_date + time_period 组合**跳过**，只补空位。返回“跳过 N 条、新建 M 条”。

## 修改排班

全字段可编辑（医生、日期、班次、号源数）。修改后若 status=PUBLISHED 则同步更新 Redis。修改 total_slots 时约束：`new_total >= (total_slots - remaining_slots)`（不得少于已用数），否则 400。修改 doctor_id / schedule_date / time_period 仍需满足 UNIQUE 冲突校验和日期窗口约束。

## 手动调整号源

放宽 schema CHECK：允许 `remaining_slots > total_slots`（加号场景），仅保留 `remaining_slots >= 0`。减少余量不可低于 0。

## 日期约束

`schedule_date >= CURRENT_DATE`（不可排过去）且 `<= CURRENT_DATE + 14`（最多排 14 天后）。

## C 端查询

本 ticket 不含 C端查询 API，C 端号源查询留给 05（挂号）ticket。本 ticket 保证 Redis 数据正确即可。

**Considered Options**: 自由时间区间 + 重叠检测（否决，前端交互复杂且演示系统无此精度需求）、草稿→发布两步流程（否决，schema 无 DRAFT 态，演示系统无审批流）、批量复制全量覆盖（否决，会违反"有挂号不可删"约束）、remaining 不可超 total（否决，加号是真实场景，改 total 语义不对）。

**Consequences**: 新增 Flyway 迁移脚本 `V04_1__schedule_add_time_period.sql`：加 `time_period` 列 + UNIQUE 约束 + 放宽 CHECK。班次枚举硬编码在 Java 枚举类中，新增班次需改代码+迁移——对 demo 可接受。14 天窗口意味着管理员只能排两周内的班，超期需等日期滚动。批量复制跳过策略不会报错，管理员需看返回结果确认跳过数。
