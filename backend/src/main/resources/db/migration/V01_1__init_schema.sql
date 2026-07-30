-- ============================================================================
-- SmartMed 数据库 DDL - 业务表结构（20 张业务表 + kb_embedding 向量表）+ 表/字段注释
-- 主键: BIGSERIAL (自增 BIGINT, 见 CONTEXT)
-- 删除策略: 物理删除 (DELETE, 见 CONTEXT)
-- 时间: TIMESTAMPTZ
-- 扩展: pgvector (CREATE EXTENSION vector)
-- 注释: COMMENT ON 紧跟每张表，便于 psql \d+ / DataGrip 查看
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
COMMENT ON TABLE hospital IS '医院信息（种子数据预设唯一一家三甲医院，B 端不提供医院管理页面，见 CONTEXT 术语表）';
COMMENT ON COLUMN hospital.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN hospital.name IS '医院名称';
COMMENT ON COLUMN hospital.level IS '医院等级，如“三甲”';
COMMENT ON COLUMN hospital.address IS '医院地址';
COMMENT ON COLUMN hospital.phone IS '医院联系电话';
COMMENT ON COLUMN hospital.intro IS '医院简介';
COMMENT ON COLUMN hospital.logo_url IS '医院 Logo 图片地址';
COMMENT ON COLUMN hospital.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN hospital.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 2. sys_user 系统登录账号 (B 端 ADMIN/DOCTOR 登录, 与 doctor 业务实体分离)
--    patient 不走此表 (C 端 demo-login 按预设 patient.id 签 JWT, patient 无密码列)
--    登录键为 phone (NOT NULL UNIQUE), 见 ADR-0004; username 降级为展示标签
--    (ADMIN 固定"管理员", DOCTOR 取医生姓名, 允许重复/NULL), 见 ADR-0004
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user (
    id                   BIGSERIAL    PRIMARY KEY,
    username             VARCHAR(64),                    -- 展示标签, 非登录键 (ADR-0004: 去 UNIQUE, 允许 NULL)
    phone                VARCHAR(32)  NOT NULL UNIQUE,   -- 登录键 (ADR-0004)
    password_hash        VARCHAR(128) NOT NULL,          -- BCrypt 哈希 ($2b$10$...), 见 01 ticket 跨 ticket 耦合说明
    role                 VARCHAR(16)  NOT NULL,          -- 枚举: ADMIN / DOCTOR
    doctor_id            BIGINT,                         -- 关联 doctor.id (DOCTOR 角色必填, ADMIN 可空)
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE, -- 首登改密标志 (ADR-0005)
    status               SMALLINT     NOT NULL DEFAULT 1,    -- 1=启用 0=禁用
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_sys_user_role CHECK (role IN ('ADMIN', 'DOCTOR'))
);
CREATE INDEX idx_sys_user_doctor_id ON sys_user (doctor_id);
COMMENT ON TABLE sys_user IS '系统登录账号（B 端 ADMIN/DOCTOR 登录），与 doctor 业务实体分离（CONTEXT §2）；patient 不走此表（C 端 demo-login 免密）';
COMMENT ON COLUMN sys_user.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN sys_user.username IS '用户名（展示标签，非登录键；ADMIN 固定“管理员”，DOCTOR 取医生姓名，允许重复/NULL，ADR-0004）';
COMMENT ON COLUMN sys_user.phone IS '登录手机号（NOT NULL UNIQUE，登录键，ADR-0004）';
COMMENT ON COLUMN sys_user.password_hash IS '密码 BCrypt 哈希（$2b$10$... 格式，Spring BCryptPasswordEncoder 兼容）';
COMMENT ON COLUMN sys_user.role IS '角色枚举：ADMIN / DOCTOR（CHECK 约束保证取值）';
COMMENT ON COLUMN sys_user.doctor_id IS '关联 doctor.id（DOCTOR 角色必填，ADMIN 为 NULL）';
COMMENT ON COLUMN sys_user.must_change_password IS '首登改密标志：true 表示首次登录须改密（ADR-0005）';
COMMENT ON COLUMN sys_user.status IS '账号状态：1=启用 0=禁用（禁用后登录被拒）';
COMMENT ON COLUMN sys_user.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN sys_user.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE patient IS '患者档案（C 端，无密码列，demo-login 按 patient.id=1 免密签 JWT）';
COMMENT ON COLUMN patient.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN patient.name IS '患者姓名';
COMMENT ON COLUMN patient.phone IS '患者手机号（C 端脱敏展示，B 端明文展示）';
COMMENT ON COLUMN patient.gender IS '性别：男 / 女';
COMMENT ON COLUMN patient.birth_date IS '出生日期（年龄由此派生计算，不单独存 age）';
COMMENT ON COLUMN patient.allergy_history IS '过敏史（选填，禁忌检测使用）';
COMMENT ON COLUMN patient.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN patient.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE patient_family_member IS '家庭成员档案（多成员健康管理，PRD 5.6）';
COMMENT ON COLUMN patient_family_member.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN patient_family_member.patient_id IS '所属患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN patient_family_member.name IS '成员姓名';
COMMENT ON COLUMN patient_family_member.relationship IS '与患者关系：本人/父母/子女/配偶/其他亲人/朋友';
COMMENT ON COLUMN patient_family_member.phone IS '成员手机号';
COMMENT ON COLUMN patient_family_member.gender IS '性别：男 / 女';
COMMENT ON COLUMN patient_family_member.birth_date IS '出生日期';
COMMENT ON COLUMN patient_family_member.allergy_history IS '过敏史';
COMMENT ON COLUMN patient_family_member.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN patient_family_member.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 5. department 科室 (归属 hospital, 03 新增时 hospital_id 硬编码为唯一医院 id=1)
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
COMMENT ON TABLE department IS '科室（归属 hospital，03 新增时 hospital_id 硬编码为唯一医院 id=1）';
COMMENT ON COLUMN department.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN department.hospital_id IS '所属医院 ID（外键 -> hospital.id，唯一医院种子 id=1）';
COMMENT ON COLUMN department.name IS '科室名称（与 Neo4j 图谱科室名对齐）';
COMMENT ON COLUMN department.description IS '科室描述';
COMMENT ON COLUMN department.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN department.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 6. doctor 医生 (关联科室, PRD 6.3)
--    phone 不落本表, 单源镜像到 sys_user.phone, 展示靠 JOIN (ADR-0005)
--    年龄由 birth_date 派生计算, 不存 age 列 (与 patient 口径一致, grill Q4)
-- ---------------------------------------------------------------------------
CREATE TABLE doctor (
    id              BIGSERIAL    PRIMARY KEY,
    department_id   BIGINT       NOT NULL,
    name            VARCHAR(64)  NOT NULL,                 -- 姓名, 同步写 sys_user.username (ADR-0005)
    gender          VARCHAR(8),                            -- 男/女 (03 新增, 与 patient.gender 一致)
    birth_date      DATE,                                  -- 出生日期, 年龄派生计算 (03 新增, grill Q4)
    title           VARCHAR(32),                           -- 职称: 主任医师/副主任医师/主治医师/住院医师
    specialty       VARCHAR(256),                          -- 擅长领域
    avatar_url      VARCHAR(512),
    intro           TEXT,
    good_rate       NUMERIC(5,2),                          -- 好评率, 百分比, 手动录入
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id) REFERENCES department (id)
);
CREATE INDEX idx_doctor_department_id ON doctor (department_id);
COMMENT ON TABLE doctor IS '医生（关联科室，PRD 6.3）；手机号/登录账号存 sys_user（单源镜像，doctor 表不存 phone）';
COMMENT ON COLUMN doctor.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN doctor.department_id IS '所属科室 ID（外键 -> department.id）';
COMMENT ON COLUMN doctor.name IS '医生姓名（新建/改名时同步写入 sys_user.username，ADR-0005）';
COMMENT ON COLUMN doctor.gender IS '性别：男 / 女（与 patient.gender 一致）';
COMMENT ON COLUMN doctor.birth_date IS '出生日期（年龄由此派生计算，不单独存 age）';
COMMENT ON COLUMN doctor.title IS '职称：主任医师 / 副主任医师 / 主治医师 / 住院医师';
COMMENT ON COLUMN doctor.specialty IS '擅长领域';
COMMENT ON COLUMN doctor.avatar_url IS '头像图片地址';
COMMENT ON COLUMN doctor.intro IS '医生简介';
COMMENT ON COLUMN doctor.good_rate IS '好评率（百分比，手动录入，如 98.00）';
COMMENT ON COLUMN doctor.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN doctor.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE schedule IS '排班（号源主数据，total_slots/remaining_slots，CONTEXT 第 7 节）';
COMMENT ON COLUMN schedule.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN schedule.doctor_id IS '出诊医生 ID（外键 -> doctor.id）';
COMMENT ON COLUMN schedule.department_id IS '所属科室 ID（外键 -> department.id）';
COMMENT ON COLUMN schedule.schedule_date IS '出诊日期';
COMMENT ON COLUMN schedule.start_time IS '时段开始时间';
COMMENT ON COLUMN schedule.end_time IS '时段结束时间（CHECK: start_time < end_time）';
COMMENT ON COLUMN schedule.total_slots IS '号源总数（CHECK: >= 0）';
COMMENT ON COLUMN schedule.remaining_slots IS '剩余号源（CHECK: 0 <= remaining_slots <= total_slots）';
COMMENT ON COLUMN schedule.status IS '排班状态：PUBLISHED（已发布）/ SUSPENDED（停诊）';
COMMENT ON COLUMN schedule.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN schedule.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE registration IS '挂号记录（关联患者、排班、医生）';
COMMENT ON COLUMN registration.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN registration.patient_id IS '挂号患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN registration.schedule_id IS '排班 ID（外键 -> schedule.id）';
COMMENT ON COLUMN registration.doctor_id IS '接诊医生 ID（外键 -> doctor.id）';
COMMENT ON COLUMN registration.reg_no IS '挂号单号（唯一，如 REG20260729001）';
COMMENT ON COLUMN registration.status IS '挂号状态：REGISTERED（已挂号）/ VISITED（已就诊）/ CANCELLED（已取消）';
COMMENT ON COLUMN registration.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN registration.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE consultation IS '问诊（关联挂号，PRD 6.6）';
COMMENT ON COLUMN consultation.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN consultation.registration_id IS '挂号 ID（外键 -> registration.id）';
COMMENT ON COLUMN consultation.patient_id IS '患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN consultation.doctor_id IS '接诊医生 ID（外键 -> doctor.id）';
COMMENT ON COLUMN consultation.pre_diagnosis IS 'AI 预问诊摘要（标注“AI 生成仅供参考”）';
COMMENT ON COLUMN consultation.diagnosis IS '医生诊断';
COMMENT ON COLUMN consultation.status IS '问诊状态：WAITING（待接诊）/ IN_PROGRESS（进行中）/ COMPLETED（已完成）';
COMMENT ON COLUMN consultation.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN consultation.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE consultation_message IS '问诊消息（医生与患者图文对话）';
COMMENT ON COLUMN consultation_message.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN consultation_message.consultation_id IS '所属问诊 ID（外键 -> consultation.id）';
COMMENT ON COLUMN consultation_message.sender_type IS '发送方类型：DOCTOR / PATIENT';
COMMENT ON COLUMN consultation_message.content IS '消息内容';
COMMENT ON COLUMN consultation_message.created_at IS '创建时间（UTC，带时区）';

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
COMMENT ON TABLE prescription IS '处方（关联问诊，保存即生效，无需药师审核，PRD 6.6.3）';
COMMENT ON COLUMN prescription.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN prescription.consultation_id IS '所属问诊 ID（外键 -> consultation.id）';
COMMENT ON COLUMN prescription.patient_id IS '患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN prescription.doctor_id IS '开方医生 ID（外键 -> doctor.id）';
COMMENT ON COLUMN prescription.diagnosis IS '诊断';
COMMENT ON COLUMN prescription.advice IS '医嘱';
COMMENT ON COLUMN prescription.status IS '处方状态：ACTIVE（生效）/ REVOKED（已撤销）';
COMMENT ON COLUMN prescription.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN prescription.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE drug IS '药品基础信息（PRD 6.7），供处方与禁忌检测使用';
COMMENT ON COLUMN drug.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN drug.name IS '药品名称（与 Neo4j 图谱药品名对齐）';
COMMENT ON COLUMN drug.specification IS '规格（如 0.3g×20 粒）';
COMMENT ON COLUMN drug.manufacturer IS '生产厂家';
COMMENT ON COLUMN drug.price IS '药品基准价格';
COMMENT ON COLUMN drug.dosage_form IS '剂型：片剂 / 胶囊 / 注射剂 / 散剂等';
COMMENT ON COLUMN drug.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN drug.updated_at IS '更新时间（UTC，带时区）';

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
COMMENT ON TABLE prescription_item IS '处方明细（药品 + 用法用量）';
COMMENT ON COLUMN prescription_item.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN prescription_item.prescription_id IS '所属处方 ID（外键 -> prescription.id）';
COMMENT ON COLUMN prescription_item.drug_id IS '药品 ID（外键 -> drug.id）';
COMMENT ON COLUMN prescription_item.usage_method IS '用法：口服 / 外用 / 注射';
COMMENT ON COLUMN prescription_item.dosage IS '每次用量';
COMMENT ON COLUMN prescription_item.frequency IS '用药频率（如每日 2 次）';
COMMENT ON COLUMN prescription_item.remark IS '备注';

-- ---------------------------------------------------------------------------
-- 14. prescription_template 处方模板 (医生个人模板, PRD 6.8)
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
COMMENT ON TABLE prescription_template IS '处方模板（医生个人模板，PRD 6.8）';
COMMENT ON COLUMN prescription_template.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN prescription_template.doctor_id IS '所属医生 ID（外键 -> doctor.id）';
COMMENT ON COLUMN prescription_template.name IS '模板名称';
COMMENT ON COLUMN prescription_template.applicable_diagnosis IS '适用诊断';
COMMENT ON COLUMN prescription_template.content IS '药品列表 + 用量用法（结构化 JSON）';
COMMENT ON COLUMN prescription_template.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN prescription_template.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 15. pharmacy 药店信息 (PRD 5.2.6)
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
COMMENT ON TABLE pharmacy IS '药店信息（PRD 5.2.6 购药对比）';
COMMENT ON COLUMN pharmacy.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN pharmacy.name IS '药店名称';
COMMENT ON COLUMN pharmacy.address IS '药店地址';
COMMENT ON COLUMN pharmacy.phone IS '药店联系电话';
COMMENT ON COLUMN pharmacy.latitude IS '纬度（用于距离计算）';
COMMENT ON COLUMN pharmacy.longitude IS '经度（用于距离计算）';
COMMENT ON COLUMN pharmacy.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN pharmacy.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 16. drug_pharmacy_stock 药店库存桥表 (见 ADR-0002)
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
COMMENT ON TABLE drug_pharmacy_stock IS '药店库存桥表（见 ADR-0002），表达“某药店有某药、卖多少、余多少”，购药对比的数据来源';
COMMENT ON COLUMN drug_pharmacy_stock.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN drug_pharmacy_stock.drug_id IS '药品 ID（外键 -> drug.id）';
COMMENT ON COLUMN drug_pharmacy_stock.pharmacy_id IS '药店 ID（外键 -> pharmacy.id）';
COMMENT ON COLUMN drug_pharmacy_stock.price IS '该药店售价（可与 drug.price 不同）';
COMMENT ON COLUMN drug_pharmacy_stock.stock IS '库存数量（CHECK: >= 0）';
COMMENT ON COLUMN drug_pharmacy_stock.distance_m IS '距用户距离（米，用于购药对比）';
COMMENT ON COLUMN drug_pharmacy_stock.delivery_eta_min IS '预计送达分钟数';
COMMENT ON COLUMN drug_pharmacy_stock.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN drug_pharmacy_stock.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 17. drug_order 购药订单 (PRD 5.2.6, 草稿存 Redis 不落此表)
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
COMMENT ON TABLE drug_order IS '购药订单（PRD 5.2.6，草稿存 Redis 不落此表，确认后才生成订单行）';
COMMENT ON COLUMN drug_order.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN drug_order.patient_id IS '购药患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN drug_order.prescription_id IS '关联处方 ID（外键 -> prescription.id）';
COMMENT ON COLUMN drug_order.pharmacy_id IS '购药药店 ID（外键 -> pharmacy.id）';
COMMENT ON COLUMN drug_order.total_amount IS '订单总金额';
COMMENT ON COLUMN drug_order.status IS '订单状态：PENDING / PAID / DELIVERING / COMPLETED / CANCELLED';
COMMENT ON COLUMN drug_order.delivery_info IS '配送信息';
COMMENT ON COLUMN drug_order.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN drug_order.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 18. chat_session 对话会话 (C 端 AI Agent 对话, PRD 5.2)
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
COMMENT ON TABLE chat_session IS '对话会话（C 端 AI Agent 对话，PRD 5.2）';
COMMENT ON COLUMN chat_session.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN chat_session.patient_id IS '发起会话的患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN chat_session.title IS '会话标题';
COMMENT ON COLUMN chat_session.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN chat_session.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 19. chat_message 对话消息
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
COMMENT ON TABLE chat_message IS '对话消息（C 端 AI Agent 对话内容）';
COMMENT ON COLUMN chat_message.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN chat_message.session_id IS '所属会话 ID（外键 -> chat_session.id）';
COMMENT ON COLUMN chat_message.role IS '消息角色：USER / ASSISTANT / TOOL';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.tool_trace IS '工具调用轨迹（JSON，PRD C18）';
COMMENT ON COLUMN chat_message.created_at IS '创建时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 20. medication_reminder 用药提醒 (PRD 5.7, 购药后按处方频次生成)
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
COMMENT ON TABLE medication_reminder IS '用药提醒（PRD 5.7，购药后按处方频次生成）';
COMMENT ON COLUMN medication_reminder.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN medication_reminder.patient_id IS '患者 ID（外键 -> patient.id）';
COMMENT ON COLUMN medication_reminder.prescription_id IS '关联处方 ID（外键 -> prescription.id）';
COMMENT ON COLUMN medication_reminder.drug_id IS '药品 ID（外键 -> drug.id）';
COMMENT ON COLUMN medication_reminder.next_remind_at IS '下次提醒时间';
COMMENT ON COLUMN medication_reminder.frequency IS '用药频率（如“每日 2 次”）';
COMMENT ON COLUMN medication_reminder.dosage IS '本次用量';
COMMENT ON COLUMN medication_reminder.remark IS '备注';
COMMENT ON COLUMN medication_reminder.status IS '提醒状态：ACTIVE（生效）/ DONE（已完成）';
COMMENT ON COLUMN medication_reminder.created_at IS '创建时间（UTC，带时区）';
COMMENT ON COLUMN medication_reminder.updated_at IS '更新时间（UTC，带时区）';

-- ---------------------------------------------------------------------------
-- 21. kb_embedding 知识库向量表 (备用 RAG, 见 ADR-0001)
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
COMMENT ON TABLE kb_embedding IS '知识库向量表（备用 RAG，见 ADR-0001）；01 阶段仅建表+索引，不导 embedding 数据，向量化逻辑留待 09/11';
COMMENT ON COLUMN kb_embedding.id IS '主键，自增 BIGINT';
COMMENT ON COLUMN kb_embedding.source_type IS '来源类型：guideline / faq / prescription_text 等';
COMMENT ON COLUMN kb_embedding.source_id IS '关联业务实体 ID（纯文本则为 NULL）';
COMMENT ON COLUMN kb_embedding.content IS '原文分块内容';
COMMENT ON COLUMN kb_embedding.embedding IS '向量（维度 1536，对齐通义 text-embedding-v2，见 ADR-0001）';
COMMENT ON COLUMN kb_embedding.metadata IS '元数据（JSON）';
COMMENT ON COLUMN kb_embedding.created_at IS '创建时间（UTC，带时区）';
