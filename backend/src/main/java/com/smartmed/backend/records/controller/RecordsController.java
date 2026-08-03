package com.smartmed.backend.records.controller;

import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.consultation.dto.ConsultationVO;
import com.smartmed.backend.consultation.dto.MedicalRecordVO;
import com.smartmed.backend.consultation.dto.MessageVO;
import com.smartmed.backend.order.dto.DrugOrderVO;
import com.smartmed.backend.order.dto.ReminderVO;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.records.service.RecordsService;
import com.smartmed.backend.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端记录查询接口（08 ticket）。
 * <p>
 * 路径：/api/c/records，需 typ=C JWT。
 * <p>
 * 全部记录按"实际就诊人"筛选（ADR-0010 口径）：
 * {@code familyMemberId} 为 0/空 → 本人；否则 → 指定家庭成员。
 */
@RestController
@RequestMapping("/api/c/records")
@RequiredArgsConstructor
public class RecordsController {

    private final RecordsService recordsService;

    // ==================== 问诊记录 ====================

    /** 问诊记录列表（编号、医生、时间、诊断摘要、状态）。 */
    @GetMapping("/consultations")
    public Result<PageResponse<ConsultationVO>> pageConsultations(
            @RequestParam(required = false) Long familyMemberId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        Long patientId = SecurityUtil.current().getPatientId();
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(recordsService.pageConsultations(patientId, normalizeMember(familyMemberId), pageNum, pageSize));
    }

    /** 问诊详情（预问诊摘要 + 诊断）。 */
    @GetMapping("/consultations/{id}")
    public Result<ConsultationVO> getConsultation(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.getConsultation(patientId, id));
    }

    /** 问诊对话记录（按时间升序）。 */
    @GetMapping("/consultations/{id}/messages")
    public Result<List<MessageVO>> listMessages(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.listMessages(patientId, id));
    }

    /** 该问诊的处方列表。 */
    @GetMapping("/consultations/{id}/prescriptions")
    public Result<List<PrescriptionVO>> listConsultationPrescriptions(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.listConsultationPrescriptions(patientId, id));
    }

    // ==================== 处方记录 ====================

    /** 处方列表（编号、开方医生、诊断、日期）。 */
    @GetMapping("/prescriptions")
    public Result<PageResponse<PrescriptionVO>> pagePrescriptions(
            @RequestParam(required = false) Long familyMemberId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        Long patientId = SecurityUtil.current().getPatientId();
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(recordsService.pagePrescriptions(patientId, normalizeMember(familyMemberId), pageNum, pageSize));
    }

    /** 处方详情（药品、用法、开方医生、日期）。 */
    @GetMapping("/prescriptions/{id}")
    public Result<PrescriptionVO> getPrescription(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.getPrescription(patientId, id));
    }

    // ==================== 购药订单 ====================

    /** 订单列表（药店、金额、配送状态）。 */
    @GetMapping("/orders")
    public Result<PageResponse<DrugOrderVO>> pageOrders(
            @RequestParam(required = false) Long familyMemberId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        Long patientId = SecurityUtil.current().getPatientId();
        if (pageSize > 100) {
            pageSize = 100;
        }
        return Result.success(recordsService.pageOrders(patientId, normalizeMember(familyMemberId), pageNum, pageSize));
    }

    /** 订单详情。 */
    @GetMapping("/orders/{id}")
    public Result<DrugOrderVO> getOrder(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.getOrder(patientId, id));
    }

    // ==================== 用药提醒 ====================

    /** 提醒列表（药品名、用量、频次、下次提醒时间）。 */
    @GetMapping("/reminders")
    public Result<List<ReminderVO>> listReminders(@RequestParam(required = false) Long familyMemberId) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.listReminders(patientId, normalizeMember(familyMemberId)));
    }

    // ==================== 健康档案 ====================

    /** 成员健康档案汇总（基本信息 + 就诊/用药汇总）。 */
    @GetMapping("/health-profile")
    public Result<MedicalRecordVO> healthProfile(@RequestParam(required = false) Long familyMemberId) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(recordsService.getHealthProfile(patientId, normalizeMember(familyMemberId)));
    }

    /** 0/空 → null（本人），其余原样传递。 */
    private Long normalizeMember(Long familyMemberId) {
        return familyMemberId != null && familyMemberId > 0 ? familyMemberId : null;
    }
}
