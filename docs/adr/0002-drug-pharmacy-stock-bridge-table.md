# 0002 - 新增 drug_pharmacy_stock 药店库存桥表

PRD 5.2.6 要求"按处方药品查附近有货药店，对比价格、距离、时效"，但原 19 张表里 drug（基础信息）与 pharmacy（药店信息）之间无关联表，无法表达"某药店有某药、卖多少钱、余多少库存"。新增 `drug_pharmacy_stock` 作为第 20 张表，承担商品目录-库存-定价职责。主键遵循 CONTEXT §2 全局策略（自增 BIGINT `id BIGSERIAL`），另加 `UNIQUE(drug_id, pharmacy_id)` 保证同药同店唯一。否决"塞进 drug_order 订单记录"：订单是历史快照，不该承担商品目录职责，且查"附近有货药店"时无表可查。否决"Java 内 Mock"：与项目 PG 物理删除/结构化数据的惯例不一致，多药多店对比数据硬编码难维护。

**Considered Options**: 建 drug_pharmacy_stock 表（采用）、塞进 drug_order（否决，职责混淆）、Java 内 Mock（否决，违背数据落库惯例）。
