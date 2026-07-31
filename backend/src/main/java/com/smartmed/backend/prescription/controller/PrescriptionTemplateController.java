package com.smartmed.backend.prescription.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.prescription.dto.PrescriptionTemplateRequest;
import com.smartmed.backend.prescription.dto.PrescriptionTemplateVO;
import com.smartmed.backend.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 处方模板接口（06 ticket，仅 DOCTOR）。
 * <p>
 * 路径：/api/b/prescription-templates，需 typ=B + role=DOCTOR。
 * 归属 doctor_id 限本人，跨医生 403。
 */
@RestController
@RequestMapping("/api/b/prescription-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class PrescriptionTemplateController {

    private final PrescriptionService prescriptionService;

    /** 本人模板列表（分页）。 */
    @GetMapping
    public Result<PageResponse<PrescriptionTemplateVO>> pageTemplates(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(prescriptionService.pageTemplates(pageNum, pageSize));
    }

    /** 新建模板。 */
    @PostMapping
    public Result<PrescriptionTemplateVO> createTemplate(@Valid @RequestBody PrescriptionTemplateRequest req) {
        return Result.success(prescriptionService.createTemplate(req));
    }

    /** 编辑模板。 */
    @PutMapping("/{id}")
    public Result<PrescriptionTemplateVO> updateTemplate(@PathVariable Long id,
                                                         @Valid @RequestBody PrescriptionTemplateRequest req) {
        return Result.success(prescriptionService.updateTemplate(id, req));
    }

    /** 删除模板。 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        prescriptionService.deleteTemplate(id);
        return Result.success();
    }
}
