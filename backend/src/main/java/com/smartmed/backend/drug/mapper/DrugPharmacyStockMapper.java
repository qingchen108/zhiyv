package com.smartmed.backend.drug.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 药店库存桥表只读 Mapper（03 仅用于药品删除前置检查，ADR-0006）。
 * <p>
 * 不建完整实体/Service，drug_pharmacy_stock 的完整 CRUD 留待购药相关 ticket。
 */
@Mapper
public interface DrugPharmacyStockMapper {

    /** 统计某药品的库存记录数（>0 则禁止删除该药品）。 */
    @Select("SELECT COUNT(*) FROM drug_pharmacy_stock WHERE drug_id = #{drugId}")
    long countByDrugId(Long drugId);
}
