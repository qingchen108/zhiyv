-- ============================================================================
-- SmartMed 数据库 DDL - 业务表结构（20 张业务表 + kb_embedding 向量表）
-- 主键: BIGSERIAL (自增 BIGINT, 见 CONTEXT)
-- 删除策略: 物理删除 (DELETE, 见 CONTEXT)
-- 时间: TIMESTAMPTZ
-- 扩展: pgvector (CREATE EXTENSION vector)
-- ============================================================================

-- pgvector 扩展 (备用 RAG, 见 ADR-0001)
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- 1. hospital 医院信息 (种子数据预设唯一一家三甲医院, 见 ADR 讨论与 PRD 6.1)
-- ---------------------------------------------------------------------------
CREATE TABLE hospital (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(128) NOT NULL,
    level        VARCHAR(32)  NOT NULL,          -- 等级, 如 "三甲"
    address      VARCHAR(256),
    phone        VARCHAR(32),
    intro        TEXT,                            -- 简介
    logo_url     VARCHAR(512),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 2. sys_user 系统登录账号 (B 端 ADMIN/DOCTOR 登录, 与 doctor 业务实体分离)
--    patient 不走此表 (C 端 demo-login 按预设 patient.id 签 JWT, patient 无密码列)
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(128) NOT NULL,        -- BCrypt 哈希 ($2b$10$...), 见 01 ticket 跨 ticket 耦合说明
    role            VARCHAR(16)  NOT NULL,        -- 枚举: ADMIN / DOCTOR
    doctor_id       BIGINT,                        -- 关联 doctor.id (DOCTOR 角色必填, ADMIN 可空)
    status          SMALLINT     NOT NULL DEFAULT 1, -- 1=启用 0=禁用
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sys_user_role CHECK (role IN ('ADMIN', 'DOCTOR'))
);
CREATE INDEX idx_sys_user_doctor_id ON sys_user (doctor_id);

-- ---------------------------------------------------------------------------
-- 3. patient 患者档案 (C 端, 无密码列, demo-login 免密)
-- ---------------------------------------------------------------------------
CREATE TABLE patient (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    phone           VARCHAR(32),                   -- 脱敏展示 (C 端), 明文展示 (B 端)
    gender          VARCHAR(8),                    -- 男/女
    birth_date      DATE,
    allergy_history TEXT,                          -- 过敏史, 选填, 禁忌检测使用
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 4. patient_family_member 家庭成员档案 (多成员健康管理, PRD 5.6)
-- ---------------------------------------------------------------------------
CREATE TABLE patient_family_member (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    relationship    VARCHAR(32)  NOT NULL,         -- 本人/父母/子女/配偶/其他亲人/朋友
    phone           VARCHAR(32),
    gender          VARCHAR(8),
    birth_date      DATE,
    allergy_history TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_family_member_patient FOREIGN KEY (patient_id) REFERENCES patient (id)
);
CREATE INDEX idx_family_member_patient_id ON patient_family_member (patient_id);

-- ---------------------------------------------------------------------------
-- 5. department 科室 (归属 hospital)
-- ---------------------------------------------------------------------------
CREATE TABLE department (
    id              BIGSERIAL    PRIMARY KEY,
    hospital_id     BIGINT       NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    description     VARCHAR(256),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_department_hospital FOREIGN KEY (hospital_id) REFERENCES hospital (id)
);
CREATE INDEX idx_department_hospital_id ON department (hospital_id);

-- ---------------------------------------------------------------------------
-- 6. doctor 医生 (关联科室, PRD 6.3)
-- ---------------------------------------------------------------------------
CREATE TABLE doctor (
    id              BIGSERIAL    PRIMARY KEY,
    department_id   BIGINT       NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    title           VARCHAR(32),                   -- 职称: 主任医师/副主任医师/主治医师/住院医师
    specialty       VARCHAR(256),                  -- 擅长领域
    avatar_url      VARCHAR(512),
    intro           TEXT,
    good_rate       NUMERIC(5,2),                  -- 好评率, 百分比
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id) REFERENCES department (id)
);
CREATE INDEX idx_doctor_department_id ON doctor (department_id);

-- ---------------------------------------------------------------------------
-- 7. schedule 排班 (号源主数据, total_slots/remaining_slots, CONTEXT 第 7 节)
-- ---------------------------------------------------------------------------
CREATE TABLE schedule (
    id               BIGSERIAL    PRIMARY KEY,
    doctor_id        BIGINT       NOT NULL,
    department_id    BIGINT       NOT NULL,
    schedule_date    DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    total_slots      INT          NOT NULL,
    remaining_slots  INT          NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED', -- PUBLISHED / SUSPENDED
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_schedule_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT fk_schedule_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT chk_schedule_slots CHECK (total_slots >= 0 AND remaining_slots >= 0 AND remaining_slots <= total_slots),
    CONSTRAINT chk_schedule_time CHECK (start_time < end_time),
    CONSTRAINT chk_schedule_status CHECK (status IN ('PUBLISHED', 'SUSPENDED'))
);
CREATE INDEX idx_schedule_doctor_date ON schedule (doctor_id, schedule_date);
CREATE INDEX idx_schedule_dept_doctor_date ON schedule (department_id, doctor_id, schedule_date);

-- ---------------------------------------------------------------------------
-- 8. registration 挂号记录
-- ---------------------------------------------------------------------------
CREATE TABLE registration (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    schedule_id     BIGINT       NOT NULL,
    doctor_id       BIGINT       NOT NULL,
    reg_no          VARCHAR(32)  NOT NULL UNIQUE,  -- 挂号单号, 如 REG20260729001
    status          VARCHAR(16)  NOT NULL DEFAULT 'REGISTERED', -- REGISTERED/VISITED/CANCELLED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_registration_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_registration_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT fk_registration_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT chk_registration_status CHECK (status IN ('REGISTERED', 'VISITED', 'CANCELLED'))
);
CREATE INDEX idx_registration_doctor_date ON registration (doctor_id, created_at);
CREATE INDEX idx_registration_patient_status ON registration (patient_id, status);

-- ---------------------------------------------------------------------------
-- 9. consultation 问诊 (关联挂号, PRD 6.6)
-- ---------------------------------------------------------------------------
CREATE TABLE consultation (
    id              BIGSERIAL    PRIMARY KEY,
    registration_id BIGINT       NOT NULL,
    patient_id      BIGINT       NOT NULL,
    doctor_id       BIGINT       NOT NULL,
    pre_diagnosis   TEXT,                           -- AI 预问诊摘要 (标注 AI 生成仅供参考)
    diagnosis       TEXT,                           -- 医生诊断
    status          VARCHAR(16)  NOT NULL DEFAULT 'WAITING', -- WAITING/IN_PROGRESS/COMPLETED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_consultation_registration FOREIGN KEY (registration_id) REFERENCES registration (id),
    CONSTRAINT fk_consultation_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_consultation_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT chk_consultation_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED'))
);
CREATE INDEX idx_consultation_patient_id ON consultation (patient_id);

-- ---------------------------------------------------------------------------
-- 10. consultation_message 问诊消息 (图文对话)
-- ---------------------------------------------------------------------------
CREATE TABLE consultation_message (
    id              BIGSERIAL    PRIMARY KEY,
    consultation_id BIGINT       NOT NULL,
    sender_type     VARCHAR(16)  NOT NULL,          -- DOCTOR / PATIENT
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_msg_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id),
    CONSTRAINT chk_msg_sender CHECK (sender_type IN ('DOCTOR', 'PATIENT'))
);
CREATE INDEX idx_consultation_msg_session_time ON consultation_message (consultation_id, created_at);

-- ---------------------------------------------------------------------------
-- 11. prescription 处方 (关联问诊, 保存即生效无需药师审核, PRD 6.6.3)
-- ---------------------------------------------------------------------------
CREATE TABLE prescription (
    id              BIGSERIAL    PRIMARY KEY,
    consultation_id BIGINT       NOT NULL,
    patient_id      BIGINT       NOT NULL,
    doctor_id       BIGINT       NOT NULL,
    diagnosis       TEXT,
    advice          TEXT,                           -- 医嘱
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / REVOKED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_prescription_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id),
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_prescription_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT chk_prescription_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);
CREATE INDEX idx_prescription_patient_id ON prescription (patient_id);

-- ---------------------------------------------------------------------------
-- 12. drug 药品基础信息 (PRD 6.7) - 先于 prescription_item 定义, 满足外键依赖
-- ---------------------------------------------------------------------------
CREATE TABLE drug (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    specification   VARCHAR(64),                    -- 规格
    manufacturer    VARCHAR(128),                   -- 生产厂家
    price           NUMERIC(10,2) NOT NULL,
    dosage_form     VARCHAR(32),                    -- 剂型: 片剂/胶囊/注射剂等
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 13. prescription_item 处方明细 (药品 + 用法用量)
-- ---------------------------------------------------------------------------
CREATE TABLE prescription_item (
    id              BIGSERIAL    PRIMARY KEY,
    prescription_id BIGINT       NOT NULL,
    drug_id         BIGINT       NOT NULL,
    usage_method    VARCHAR(32),                    -- 口服/外用/注射
    dosage          VARCHAR(64),                    -- 每次用量
    frequency       VARCHAR(32),                    -- 每日 N 次
    remark          VARCHAR(256),
    CONSTRAINT fk_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_item_drug FOREIGN KEY (drug_id) REFERENCES drug (id)
);
CREATE INDEX idx_prescription_item_prescription_id ON prescription_item (prescription_id);

-- ---------------------------------------------------------------------------
-- 13. prescription_template 处方模板 (医生个人模板, PRD 6.8)
-- ---------------------------------------------------------------------------
CREATE TABLE prescription_template (
    id              BIGSERIAL    PRIMARY KEY,
    doctor_id       BIGINT       NOT NULL,
    name            VARCHAR(64)  NOT NULL,
    applicable_diagnosis VARCHAR(128),
    content         JSONB        NOT NULL,          -- 药品列表 + 用量用法, 结构化存储
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_template_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id)
);
CREATE INDEX idx_prescription_template_doctor_id ON prescription_template (doctor_id);

-- ---------------------------------------------------------------------------
-- 14. pharmacy 药店信息 (PRD 5.2.6)
-- ---------------------------------------------------------------------------
CREATE TABLE pharmacy (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    address         VARCHAR(256),
    phone           VARCHAR(32),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 15. drug_pharmacy_stock 药店库存桥表 (见 ADR-0002)
--    表达"某药店有某药、卖多少、余多少", 购药对比的数据来源
-- ---------------------------------------------------------------------------
CREATE TABLE drug_pharmacy_stock (
    id              BIGSERIAL    PRIMARY KEY,
    drug_id         BIGINT       NOT NULL,
    pharmacy_id     BIGINT       NOT NULL,
    price           NUMERIC(10,2) NOT NULL,         -- 该药店售价 (可与 drug.price 不同)
    stock           INT          NOT NULL DEFAULT 0,
    distance_m      INT,                            -- 距用户距离 (米), 用于购药对比
    delivery_eta_min INT,                           -- 预计送达分钟数
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_stock_drug FOREIGN KEY (drug_id) REFERENCES drug (id),
    CONSTRAINT fk_stock_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy (id),
    CONSTRAINT uk_drug_pharmacy UNIQUE (drug_id, pharmacy_id),
    CONSTRAINT chk_stock CHECK (stock >= 0)
);
CREATE INDEX idx_drug_pharmacy_stock_drug_id ON drug_pharmacy_stock (drug_id);

-- ---------------------------------------------------------------------------
-- 16. drug_order 购药订单 (PRD 5.2.6, 草稿存 Redis 不落此表)
-- ---------------------------------------------------------------------------
CREATE TABLE drug_order (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    prescription_id BIGINT       NOT NULL,
    pharmacy_id     BIGINT       NOT NULL,
    total_amount    NUMERIC(10,2) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING/PAID/DELIVERING/COMPLETED/CANCELLED
    delivery_info   VARCHAR(256),                  -- 配送信息
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_order_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_order_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_order_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy (id),
    CONSTRAINT chk_order_status CHECK (status IN ('PENDING', 'PAID', 'DELIVERING', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX idx_drug_order_patient_status ON drug_order (patient_id, status);

-- ---------------------------------------------------------------------------
-- 17. chat_session 对话会话 (C 端 AI Agent 对话, PRD 5.2)
-- ---------------------------------------------------------------------------
CREATE TABLE chat_session (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    title           VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_session_patient FOREIGN KEY (patient_id) REFERENCES patient (id)
);
CREATE INDEX idx_chat_session_patient_id ON chat_session (patient_id);

-- ---------------------------------------------------------------------------
-- 18. chat_message 对话消息
-- ---------------------------------------------------------------------------
CREATE TABLE chat_message (
    id              BIGSERIAL    PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,          -- USER / ASSISTANT / TOOL
    content         TEXT         NOT NULL,
    tool_trace      JSONB,                          -- 工具调用轨迹 (PRD C18)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_chat_msg_session FOREIGN KEY (session_id) REFERENCES chat_session (id),
    CONSTRAINT chk_chat_msg_role CHECK (role IN ('USER', 'ASSISTANT', 'TOOL'))
);
CREATE INDEX idx_chat_message_session_time ON chat_message (session_id, created_at);

-- ---------------------------------------------------------------------------
-- 19. medication_reminder 用药提醒 (PRD 5.7, 购药后按处方频次生成)
-- ---------------------------------------------------------------------------
CREATE TABLE medication_reminder (
    id                  BIGSERIAL    PRIMARY KEY,
    patient_id          BIGINT       NOT NULL,
    prescription_id     BIGINT       NOT NULL,
    drug_id             BIGINT       NOT NULL,
    next_remind_at      TIMESTAMPTZ  NOT NULL,      -- 下次提醒时间
    frequency           VARCHAR(32),                -- 如 "每日2次"
    dosage              VARCHAR(64),                -- 本次用量
    remark              VARCHAR(256),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / DONE
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_reminder_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_reminder_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_reminder_drug FOREIGN KEY (drug_id) REFERENCES drug (id),
    CONSTRAINT chk_reminder_status CHECK (status IN ('ACTIVE', 'DONE'))
);
CREATE INDEX idx_medication_reminder_patient_next ON medication_reminder (patient_id, next_remind_at);

-- ---------------------------------------------------------------------------
-- kb_embedding 知识库向量表 (备用 RAG, 见 ADR-0001)
-- 01 阶段仅建表+索引, 不导任何 embedding 数据, 不碰向量化逻辑 (留待 09/11)
-- ---------------------------------------------------------------------------
CREATE TABLE kb_embedding (
    id          BIGSERIAL    PRIMARY KEY,
    source_type VARCHAR(32)  NOT NULL,              -- guideline/faq/prescription_text 等
    source_id   BIGINT,                             -- 关联业务实体 id (纯文本则空)
    content     TEXT         NOT NULL,              -- 原文分块
    embedding   vector(1536),                       -- 向量, 维度对齐通义 text-embedding-v2, 见 ADR-0001
    metadata    JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_embedding_vec ON kb_embedding USING ivfflat (embedding vector_cosine_ops);
