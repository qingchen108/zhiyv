-- 05 ticket: 挂号全链路（含并发控制）
-- ADR-0010: 家人挂号双外键

-- 1. registration 增加 family_member_id（nullable FK → patient_family_member）
ALTER TABLE registration ADD COLUMN family_member_id BIGINT NULL;
ALTER TABLE registration ADD CONSTRAINT fk_registration_family_member
    FOREIGN KEY (family_member_id) REFERENCES patient_family_member (id);

COMMENT ON COLUMN registration.family_member_id IS '实际就诊人（家庭成员 ID，NULL 表示患者本人），见 ADR-0010';

-- 2. 挂号单号序列（全局递增，REG+yyyyMMdd+LPAD(seq,3,'0')）
CREATE SEQUENCE IF NOT EXISTS reg_no_seq START 1;

-- 3. 索引：按实际就诊人查重复挂号
CREATE INDEX idx_registration_visitor ON registration (patient_id, family_member_id, schedule_id);
