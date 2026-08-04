# 13 — Agent 预问诊与处方解读

**What to build:** 挂号后 Agent 引导患者补充病情生成预问诊摘要推送给医生；医生开方后，Agent 用通俗语言解读处方内容，检测到过敏风险时高亮警告并阻断后续购药。

**Blocked by:** 09 — Agent 框架搭建, 06 — B 端医生工作台

**Status:** done

- [x] Agent 预问诊流程：引导补充主诉、现病史、既往史、确认过敏史
- [x] Java 工具实现：预问诊摘要提交（结构化摘要 → 关联挂号记录 → 推送医生）
- [x] Agent 生成摘要卡片（主诉/现病史/过敏史 + "AI 生成，仅供参考"标注）
- [x] Java 工具实现：处方查询（按患者/挂号查处方详情）
- [x] Agent 处方解读：通俗语言解释每种药（作用、吃法、注意事项）
- [x] Java 工具实现：过敏风险检测（处方药品 vs 患者过敏史，查 Neo4j CONTAINS 关系）
- [x] 检测到过敏风险 → Agent 高亮警告 + 建议联系医生 + 阻断购药流程
- [x] 解读标注"AI 解读仅供参考，请遵医嘱服用"
- [x] pytest 测试：预问诊摘要生成、处方解读、过敏阻断

## 实现概要

### Java 后端 (AgentToolDispatcher + ConsultationService)
- write_pre_diagnosis — 写入预问诊摘要到 consultation.pre_diagnosis，仅 IN_PROGRESS 可写
- get_prescription — 按 prescription_id 查询处方详情（含明细和药品名）
- check_allergy — 按 drug_names 检测过敏冲突，返回 warnings + has_allergy_risk

### Python Agent (consultation.py)
- uild_consultation_node(llm) — 预问诊与处方解读意图节点
  - **预问诊流程**：LLM 多轮对话收集主诉/现病史/既往史/过敏史 → 生成结构化摘要 + card 事件
  - **处方解读**：查询 get_prescription → LLM 通俗解释（作用/吃法/注意事项）
  - **过敏检查**：调用 check_allergy → 有风险时高亮警告 + 阻断购药
  - 解读标注"AI 解读仅供参考，请遵医嘱服用"
- intents.py 已接入 consultation 节点

### 新增工具
- check_allergy — 新增到 tools.json（12 个工具），Java AgentToolDispatcher 已实现 handler

### 测试
- 6 个测试类覆盖格式化函数、辅助函数和意图节点基础行为
