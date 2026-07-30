# 挂号表家人就诊双外键

患者可帮健康档案中的家庭成员挂号，但 `patient_family_member` 与 `patient` 是独立表。为在 `registration` 中同时记录操作人和实际就诊人，采用双外键方案：`patient_id`（NOT NULL，操作人）+ `family_member_id`（NULLABLE，实际就诊人；NULL 表示本人就诊）。

## Considered Options

- **多态关联（registrant_type + registrant_id）**：去掉 FK，查询复杂且失去数据库级完整性约束。
- **家庭成员提升为 patient**：挂号时把 family_member 复制到 patient 表，引入数据冗余和同步问题。
- **双外键（采纳）**：两个独立 FK 各指各的表，语义清晰，查询简单，完整性由数据库保障。

## Consequences

- 重复挂号校验维度为**实际就诊人**：`family_member_id IS NULL` 时按 `patient_id` 去重，否则按 `family_member_id` 去重。
- "我的挂号"查询统一用 `WHERE patient_id = ?`（包含本人 + 帮家人挂的所有记录）。
- 医生视角按实际就诊人查询时需 COALESCE 两列或 JOIN 两张表。
