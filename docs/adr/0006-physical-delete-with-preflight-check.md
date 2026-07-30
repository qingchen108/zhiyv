# 0006 - 实体删除：物理删除 + 前置引用检查（非级联删）

department / doctor / drug 三类业务实体的删除统一采用"物理删除 + 前置引用检查"：删除前先查询是否存在外键引用（如删 doctor 前查 schedule/registration/consultation 等表是否引用该 doctor_id），有引用则抛 `BusinessException(409, "...存在...记录，无法删除")` 拒绝，无引用则 `DELETE` 物理删除。删除 doctor 时同事务删其 sys_user（见 ADR-0005）。此策略对齐 CONTEXT §2"删除策略：物理删除（直接 DELETE）"，不引入软删除字段。

**Considered Options**: `ON DELETE CASCADE` 级联删（否决，医疗数据级联删是灾难，排班/挂号/处方会连带消失）、软删除加 `deleted` 列 + MyBatis-Plus 逻辑删除（否决，违反 CONTEXT 物理删除既定决策，且所有查询须加 `where deleted=false`，全局复杂度上升）、不检查直接 DELETE 靠 DB 外键报错（否决，DB 外键报错信息暴露表结构且非统一响应格式，用户体验差）。

**Consequences**: 有历史业务数据的实体（如 doctor 被 schedule 引用）删不掉，须先清引用数据再删--对 demo 可接受（03 阶段仅 schedule 有种子数据，registration/consultation 等表 05+ 才有数据，检查代码照写但 03 不触发）。前置检查范围须覆盖所有引用该实体的表，后续新表（如 prescription_item 引用 drug）须同步补检查逻辑，否则删除会漏挡。删除是同事务操作（先查引用 -> 删子关联如 sys_user -> 删本体），service 层 `@Transactional`。409 响应 message 明确指出被哪类引用阻挡，前端展示"该医生存在排班/挂号记录，无法删除"供用户排查。
