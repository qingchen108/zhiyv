# 0012 - 禁忌检测：Neo4j 直查 Cypher + 不阻断 + 文本子串匹配

开方 `POST /api/b/prescriptions` 时同步查 Neo4j 检测两类冲突，结果以 warnings 返回，**不阻断保存**：过敏冲突（`Drug -[:CONTAINS|CONTRAINDICATED_IN]-> Allergen`，比对实际就诊人过敏史文本子串）+ 药物相互作用（处方内药品两两 `INTERACTS_WITH`）。Neo4j 不可用时返回空 warnings + ERROR 日志，不阻断开方。

## Considered Options

- **Neo4j 直查 Cypher（采纳）**：用 `Neo4jClient.query()`，不建 SDN `@Node` 实体/repository。06 只读 3 种关系查询，无写操作，建实体类是过度工程。
- **SDN repository + 实体类**：对纯读、3 个查询的 demo 场景过重。
- **禁忌检测阻断开方**：否决。医生需能强制保存（医疗场景医生有裁量权），检测是辅助不阻塞主流程。
- **过敏史分词/NLP 匹配**：否决。`patient.allergy_history` 是自由 TEXT，Allergen 节点名是中文短语，子串匹配（`allergyHistory.contains(allergenName)`）对 demo 够用。

## Consequences

- 过敏史比对依赖 Allergen 节点名与过敏史文本的字面命中（如"青霉素"匹配"青霉素"过敏史），漏匹配风险存在但对种子数据可控。
- `force=true` 仅审计标记（记录"医生确认知晓 N 条冲突强制开方"），后端不靠它区分是否保存--有冲突无 force 也保存。前端用 warnings 弹窗 + force 二次提交做交互确认。
- Neo4j 降级时禁忌检测静默失效，处方仍可开具；安全兜底靠医生人工，demo 可接受。
- 不做疾病-药品禁忌（图谱无对应关系，spec 未要求）。
- Neo4j 查询收在 `knowledge` 包，未来 Agent 导诊/处方解读（ticket 11/13）的图谱查询同收此处。
