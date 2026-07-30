package com.smartmed.backend.drug.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.drug.dto.DrugRequest;
import com.smartmed.backend.drug.dto.DrugVO;
import com.smartmed.backend.drug.entity.Drug;
import com.smartmed.backend.drug.mapper.DrugMapper;
import com.smartmed.backend.drug.mapper.DrugPharmacyStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 药品服务（仅 ADMIN 操作，权限由 Controller @PreAuthorize 控制）。
 * <p>
 * 删除走物理删 + 前置引用检查（ADR-0006）：有 drug_pharmacy_stock 引用则 409 拒绝。
 * prescription_item / medication_reminder 引用检查留待 05+（03 阶段无数据）。
 */
@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugMapper drugMapper;
    private final DrugPharmacyStockMapper stockMapper;

    /** 分页查询，支持 name 模糊，固定 id ASC（Q12）。 */
    public PageResponse<DrugVO> page(long pageNum, long pageSize, String name) {
        Page<Drug> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Drug> qw = new LambdaQueryWrapper<Drug>()
                .like(name != null && !name.isBlank(), Drug::getName, name)
                .orderByAsc(Drug::getId);
        drugMapper.selectPage(page, qw);
        return PageResponse.of(page.convert(this::toVO));
    }

    public DrugVO getById(Long id) {
        Drug d = drugMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "药品不存在");
        }
        return toVO(d);
    }

    @Transactional
    public DrugVO create(DrugRequest req) {
        Drug d = new Drug();
        applyRequest(d, req);
        drugMapper.insert(d);
        return toVO(d);
    }

    @Transactional
    public DrugVO update(Long id, DrugRequest req) {
        Drug d = drugMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(404, "药品不存在");
        }
        applyRequest(d, req);
        drugMapper.updateById(d);
        return toVO(d);
    }

    /**
     * 删除药品：前置引用检查（ADR-0006）。
     * 有 drug_pharmacy_stock 引用则 409 拒绝；prescription_item / medication_reminder 留待 05+。
     */
    @Transactional
    public void delete(Long id) {
        if (drugMapper.selectById(id) == null) {
            throw new BusinessException(404, "药品不存在");
        }
        if (stockMapper.countByDrugId(id) > 0) {
            throw new BusinessException(409, "该药品存在药店库存记录，无法删除");
        }
        drugMapper.deleteById(id);
    }

    private void applyRequest(Drug d, DrugRequest req) {
        d.setName(req.getName());
        d.setSpecification(req.getSpecification());
        d.setManufacturer(req.getManufacturer());
        d.setPrice(req.getPrice());
        d.setDosageForm(req.getDosageForm());
    }

    private DrugVO toVO(Drug d) {
        return DrugVO.builder()
                .id(d.getId())
                .name(d.getName())
                .specification(d.getSpecification())
                .manufacturer(d.getManufacturer())
                .price(d.getPrice())
                .dosageForm(d.getDosageForm())
                .build();
    }
}
