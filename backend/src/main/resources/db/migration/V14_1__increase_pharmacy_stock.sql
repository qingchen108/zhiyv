-- SmartMed 14 ticket: 调大药店库存量，确保演示多次下单不耗尽
-- 将所有库存值提升到 100 以上，避免演示时库存不足
UPDATE drug_pharmacy_stock SET stock = 200, updated_at = now() WHERE stock < 200;
