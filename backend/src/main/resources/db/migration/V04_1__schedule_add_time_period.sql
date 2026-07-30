-- 04 ticket: 排班增加 time_period 枚举列 + 冲突 UNIQUE + 放宽号源 CHECK（ADR-0009）

-- 1. 新增 time_period 列（枚举：MORNING / AFTERNOON / EVENING）
ALTER TABLE schedule ADD COLUMN time_period VARCHAR(16) NOT NULL DEFAULT 'MORNING';

-- 2. 根据已有 start_time 推断 time_period（兼容种子数据）
UPDATE schedule SET time_period = CASE
    WHEN start_time < '13:00' THEN 'MORNING'
    WHEN start_time < '17:00' THEN 'AFTERNOON'
    ELSE 'EVENING'
END;

-- 3. 班次 CHECK
ALTER TABLE schedule ADD CONSTRAINT chk_schedule_time_period
    CHECK (time_period IN ('MORNING', 'AFTERNOON', 'EVENING'));

-- 4. 冲突校验：同医生 + 同日期 + 同班次 不可重复
ALTER TABLE schedule ADD CONSTRAINT uq_schedule_doctor_date_period
    UNIQUE (doctor_id, schedule_date, time_period);

-- 5. 放宽号源 CHECK：允许 remaining > total（加号场景），仅保留 >= 0
ALTER TABLE schedule DROP CONSTRAINT chk_schedule_slots;
ALTER TABLE schedule ADD CONSTRAINT chk_schedule_slots
    CHECK (total_slots >= 0 AND remaining_slots >= 0);

-- 6. 索引（号源总览按科室+日期查询）
CREATE INDEX IF NOT EXISTS idx_schedule_dept_date ON schedule (department_id, schedule_date);

COMMENT ON COLUMN schedule.time_period IS '班次枚举：MORNING(08:00-12:00)/AFTERNOON(14:00-17:30)/EVENING(18:00-21:00)，ADR-0009';
