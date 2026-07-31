-- ============================================================================
-- V06_2: department 表添加 location 列（科室位置，选填，方便患者寻找）
-- 包含：DDL（加列）+ DML（更新现有科室位置数据）
-- ============================================================================

-- 1. 添加 location 列
ALTER TABLE department ADD COLUMN IF NOT EXISTS location VARCHAR(128);

COMMENT ON COLUMN department.location IS '科室位置（如 门诊楼3层A区，选填，方便患者寻找）';

-- 2. 为现有科室填充位置数据
UPDATE department SET location = '门诊楼3层A区' WHERE id = 1;
UPDATE department SET location = '门诊楼3层B区' WHERE id = 2;
UPDATE department SET location = '门诊楼4层A区' WHERE id = 3;
UPDATE department SET location = '门诊楼4层B区' WHERE id = 4;
UPDATE department SET location = '门诊楼5层A区' WHERE id = 5;
UPDATE department SET location = '门诊楼2层A区' WHERE id = 6;
UPDATE department SET location = '门诊楼5层C区' WHERE id = 7;
UPDATE department SET location = '门诊楼6层A区' WHERE id = 8;
UPDATE department SET location = '门诊楼6层B区' WHERE id = 9;
UPDATE department SET location = '门诊楼6层C区' WHERE id = 10;
UPDATE department SET location = '住院部2层东侧' WHERE id = 11;
UPDATE department SET location = '急诊楼1层'     WHERE id = 12;
UPDATE department SET location = '住院部3层'     WHERE id = 13;
UPDATE department SET location = '门诊楼1层B区' WHERE id = 14;
UPDATE department SET location = '住院部5层西侧' WHERE id = 15;