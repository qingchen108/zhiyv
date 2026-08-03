package com.smartmed.backend.registration.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.registration.dto.*;
import com.smartmed.backend.registration.service.RegistrationService;
import com.smartmed.backend.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端挂号接口（05 ticket）。
 * <p>
 * 路径：/api/c/registrations，需 typ=C JWT。
 */
@RestController
@RequestMapping("/api/c/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /** 创建挂号草稿（校验排班 + 写 Redis TTL 30min）。 */
    @PostMapping("/draft")
    public Result<RegistrationDraftResponse> createDraft(@Valid @RequestBody RegistrationDraftRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(registrationService.createDraft(patientId, req));
    }

    /** 确认挂号（消费草稿 → Lua 扣减 → 写 PG → 返回凭证）。 */
    @PostMapping("/confirm")
    public Result<RegistrationVO> confirm(@Valid @RequestBody RegistrationConfirmRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(registrationService.confirm(patientId, req));
    }

    /** 取消挂号（就诊前 2h 以上可取消）。 */
    @PatchMapping("/{id}/cancel")
    public Result<RegistrationVO> cancel(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(registrationService.cancel(patientId, id));
    }

    /** 挂号列表（按状态筛选 + 按当前成员筛选，分页）。 */
    @GetMapping
    public Result<PageResponse<RegistrationVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long familyMemberId) {
        Long patientId = SecurityUtil.current().getPatientId();
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(registrationService.page(patientId, pageNum, pageSize, status, familyMemberId));
    }

    /** 挂号详情。 */
    @GetMapping("/{id}")
    public Result<RegistrationVO> getById(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(registrationService.getById(patientId, id));
    }
}
