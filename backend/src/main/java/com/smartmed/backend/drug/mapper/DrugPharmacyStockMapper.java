package com.smartmed.backend.drug.mapper;

import com.smartmed.backend.drug.dto.DrugPharmacyStockVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DrugPharmacyStockMapper {

    /** 统计某药品的库存记录数（>0 则禁止删除该药品）。 */
    @Select("SELECT COUNT(*) FROM drug_pharmacy_stock WHERE drug_id = #{drugId}")
    long countByDrugId(Long drugId);

    /**
     * 按 drug_id 查询各药店库存/价格/配送时效（含药店名称），返回扁平列表。
     * 用于 Agent 购药推荐对比展示。
     */
    @Select("""
            SELECT s.drug_id, d.name AS drug_name, d.specification AS drug_specification,
                   s.pharmacy_id, p.name AS pharmacy_name, p.address AS pharmacy_address,
                   s.price, s.stock, s.distance_m, s.delivery_eta_min
            FROM drug_pharmacy_stock s
            JOIN drug d ON d.id = s.drug_id
            JOIN pharmacy p ON p.id = s.pharmacy_id
            WHERE s.drug_id = #{drugId}
            ORDER BY s.price ASC
            """)
    List<DrugPharmacyStockVO> selectByDrugId(@Param("drugId") Long drugId);

    /**
     * 原子扣减库存：stock = stock - 1，仅当 stock > 0 时成功。
     * @return 受影响行数（1=成功，0=库存不足）
     */
    @Update("UPDATE drug_pharmacy_stock SET stock = stock - 1, updated_at = now() " +
            "WHERE drug_id = #{drugId} AND pharmacy_id = #{pharmacyId} AND stock > 0")
    int deductStock(@Param("drugId") Long drugId, @Param("pharmacyId") Long pharmacyId);
}
