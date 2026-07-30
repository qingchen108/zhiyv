-- ============================================================================
-- SmartMed 迁移脚本 03：02 返工 schema 变更（ADR-0004/0005）
-- 适用：已运行 02 旧 schema 的 VM 数据库（docker-entrypoint-initdb.d 不会重跑）
-- 幂等：可重复执行，已存在的列/约束会跳过
-- 变更：
--   sys_user: 加 phone(NOT NULL UNIQUE)、must_change_password；username 去 UNIQUE 允许 NULL
--   doctor:   加 gender、birth_date
--   种子数据：admin/doctor 补 phone + must_change_password=true；5 医生补 gender/birth_date
-- ============================================================================

-- ---------- sys_user: 加 phone ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
-- 先填默认值给已有行（避免 NOT NULL 约束失败），再补种子真实 phone
UPDATE sys_user SET phone = '13800000000' WHERE id = 1 AND phone IS NULL;
UPDATE sys_user SET phone = '13800000002' WHERE id = 2 AND phone IS NULL;
-- 填充其余可能存在的行（防御性，给个占位）
UPDATE sys_user SET phone = '1380000009' || id::text WHERE phone IS NULL;
ALTER TABLE sys_user ALTER COLUMN phone SET NOT NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'sys_user_phone_key') THEN
        ALTER TABLE sys_user ADD CONSTRAINT sys_user_phone_key UNIQUE (phone);
    END IF;
END $$;

-- ---------- sys_user: 加 must_change_password ----------
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------- sys_user: username 去 UNIQUE、允许 NULL ----------
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'sys_user_username_key') THEN
        ALTER TABLE sys_user DROP CONSTRAINT sys_user_username_key;
    END IF;
END $$;
ALTER TABLE sys_user ALTER COLUMN username DROP NOT NULL;

-- ---------- doctor: 加 gender、birth_date ----------
ALTER TABLE doctor ADD COLUMN IF NOT EXISTS gender VARCHAR(8);
ALTER TABLE doctor ADD COLUMN IF NOT EXISTS birth_date DATE;

-- ---------- 种子数据补全 ----------
-- admin 账号：username=管理员, phone=13800000000, must_change_password=true
UPDATE sys_user SET username = '管理员', phone = '13800000000', must_change_password = TRUE
WHERE id = 1;
-- doctor 账号：username=张呼吸, phone=13800000002, must_change_password=true
UPDATE sys_user SET username = '张呼吸', phone = '13800000002', must_change_password = TRUE
WHERE id = 2;

-- 5 个种子医生补 gender + birth_date
UPDATE doctor SET gender = '男', birth_date = '1975-03-10' WHERE id = 1 AND gender IS NULL;
UPDATE doctor SET gender = '男', birth_date = '1968-07-22' WHERE id = 2 AND gender IS NULL;
UPDATE doctor SET gender = '女', birth_date = '1978-11-05' WHERE id = 3 AND gender IS NULL;
UPDATE doctor SET gender = '男', birth_date = '1982-09-18' WHERE id = 4 AND gender IS NULL;
UPDATE doctor SET gender = '女', birth_date = '1970-02-14' WHERE id = 5 AND gender IS NULL;
