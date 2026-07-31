package com.smartmed.backend.prescription.controller;

import com.smartmed.backend.common.Result;
import com.smartmed.backend.prescription.dto.PrescriptionCreateResponse;
import com.smartmed.backend.prescription.dto.PrescriptionRequest;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 处方接口（06 ticket，仅 DOCTOR）。
 * <p>
 * 路径：/api/b/prescriptions，需 typ=B + role=DOCTOR。
 */
@RestController
@RequestMapping("/api/b/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /** 开方（含禁忌检测，返回 warnings）。 */
    @PostMapping
    public Result<PrescriptionCreateResponse> create(@Valid @RequestBody PrescriptionRequest req) {
        return Result.success(prescriptionService.create(req));
    }

    /** 处方详情（含明细 + 药品名）。 */
    @GetMapping("/{id}")
    public Result<PrescriptionVO> getById(@PathVariable Long id) {
        return Result.success(prescriptionService.getById(id));
    }
}
