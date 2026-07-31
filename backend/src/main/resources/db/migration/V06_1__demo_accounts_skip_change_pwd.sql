-- V06_1: 演示账号免首登改密（配合演示场景调整）
-- V01_2 种子已改 must_change_password=FALSE，但已迁移过的库不会重跑 V01_2，
-- 故新增本迁移把库内现存演示账号（id=1 管理员 / id=2 张呼吸）置为 false，与种子保持一致。
-- 后续新建账号仍默认 must_change_password=true 走首登改密（ADR-0005）。
UPDATE sys_user SET must_change_password = false WHERE id IN (1, 2);