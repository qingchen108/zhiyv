package com.smartmed.backend.consultation.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.consultation.dto.ConsultationVO;
import com.smartmed.backend.consultation.dto.DiagnosisRequest;
import com.smartmed.backend.consultation.dto.MedicalRecordVO;
import com.smartmed.backend.consultation.dto.MessageRequest;
import com.smartmed.backend.consultation.dto.MessageVO;
import com.smartmed.backend.consultation.service.ConsultationService;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 问诊接口（06 ticket，仅 DOCTOR）。
 * <p>
 * 路径：/api/b/consultations，需 typ=B + role=DOCTOR。
 */
@RestController
@RequestMapping("/api/b/consultations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class ConsultationController {

    private final ConsultationService consultationService;

    /** 今日待接诊列表（status=WAITING，分页）。 */
    @GetMapping("/today")
    public Result<PageResponse<ConsultationVO>> todayWaiting(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(consultationService.todayWaiting(pageNum, pageSize));
    }

    /** 问诊详情。 */
    @GetMapping("/{id}")
    public Result<ConsultationVO> getById(@PathVariable Long id) {
        return Result.success(consultationService.getById(id));
    }

    /** 接诊：WAITING -> IN_PROGRESS。 */
    @PatchMapping("/{id}/start")
    public Result<ConsultationVO> start(@PathVariable Long id) {
        return Result.success(consultationService.start(id));
    }

    /** 完成：IN_PROGRESS -> COMPLETED（同步 registration VISITED）。 */
    @PatchMapping("/{id}/complete")
    public Result<ConsultationVO> complete(@PathVariable Long id) {
        return Result.success(consultationService.complete(id));
    }

    /** 保存诊断（IN_PROGRESS 可改）。 */
    @PatchMapping("/{id}/diagnosis")
    public Result<ConsultationVO> saveDiagnosis(@PathVariable Long id,
                                                @Valid @RequestBody DiagnosisRequest req) {
        return Result.success(consultationService.saveDiagnosis(id, req));
    }

    /** 消息列表（DOCTOR + PATIENT，按时间升序）。 */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> listMessages(@PathVariable Long id) {
        return Result.success(consultationService.listMessages(id));
    }

    /** 发消息（仅 DOCTOR，仅 IN_PROGRESS）。 */
    @PostMapping("/{id}/messages")
    public Result<MessageVO> sendMessage(@PathVariable Long id,
                                         @Valid @RequestBody MessageRequest req) {
        return Result.success(consultationService.sendMessage(id, req));
    }

    /** 患者病历聚合（以实际就诊人为中心）。 */
    @GetMapping("/{id}/medical-record")
    public Result<MedicalRecordVO> medicalRecord(@PathVariable Long id) {
        return Result.success(consultationService.medicalRecord(id));
    }

    /** 该问诊的处方列表。 */
    @GetMapping("/{id}/prescriptions")
    public Result<List<PrescriptionVO>> listPrescriptions(@PathVariable Long id) {
        return Result.success(consultationService.listPrescriptionsByConsultation(id));
    }
}
