package com.smartmed.backend.patient.controller;

import com.smartmed.backend.common.Result;
import com.smartmed.backend.patient.dto.FamilyMemberRequest;
import com.smartmed.backend.patient.dto.FamilyMemberVO;
import com.smartmed.backend.patient.dto.PatientProfileUpdateRequest;
import com.smartmed.backend.patient.dto.PatientProfileVO;
import com.smartmed.backend.patient.service.PatientService;
import com.smartmed.backend.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端患者档案与家庭成员接口（07 ticket）。
 * <p>
 * 路径：/api/c/patients，需 typ=C JWT。
 */
@RestController
@RequestMapping("/api/c/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    // ==================== 患者档案 ====================

    /** 获取当前患者档案（含派生 age + 脱敏 phone）。 */
    @GetMapping("/me")
    public Result<PatientProfileVO> getProfile() {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(patientService.getProfile(patientId));
    }

    /** 更新当前患者档案（仅更新传入的非空字段）。 */
    @PutMapping("/me")
    public Result<PatientProfileVO> updateProfile(@Valid @RequestBody PatientProfileUpdateRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(patientService.updateProfile(patientId, req));
    }

    // ==================== 家庭成员 ====================

    /** 获取当前患者的家庭成员列表。 */
    @GetMapping("/family-members")
    public Result<List<FamilyMemberVO>> listFamilyMembers() {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(patientService.listFamilyMembers(patientId));
    }

    /** 新增家庭成员。 */
    @PostMapping("/family-members")
    public Result<FamilyMemberVO> addFamilyMember(@Valid @RequestBody FamilyMemberRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(patientService.addFamilyMember(patientId, req));
    }

    /** 更新家庭成员。 */
    @PutMapping("/family-members/{id}")
    public Result<FamilyMemberVO> updateFamilyMember(
            @PathVariable Long id,
            @Valid @RequestBody FamilyMemberRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(patientService.updateFamilyMember(patientId, id, req));
    }

    /** 删除家庭成员。 */
    @DeleteMapping("/family-members/{id}")
    public Result<Void> deleteFamilyMember(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        patientService.deleteFamilyMember(patientId, id);
        return Result.success();
    }
}