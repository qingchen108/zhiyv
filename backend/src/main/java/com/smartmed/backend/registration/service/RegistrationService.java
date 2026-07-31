package com.smartmed.backend.registration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.consultation.entity.Consultation;
import com.smartmed.backend.consultation.mapper.ConsultationMapper;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.registration.dto.*;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import com.smartmed.backend.registration.entity.Registration;
import com.smartmed.backend.registration.mapper.PatientFamilyMemberMapper;
import com.smartmed.backend.registration.mapper.RegistrationMapper;
import com.smartmed.backend.schedule.entity.Schedule;
import com.smartmed.backend.schedule.entity.TimePeriod;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import com.smartmed.backend.schedule.service.ScheduleRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 挂号业务服务（05 ticket，CONTEXT §7）。
 * <p>
 * 两段式：创建草稿 → 确认扣减。
 * 防刷：同就诊人同排班 5 秒限流。
 * 取消：就诊前 2h 以上可取消，号源释放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final RegistrationMapper registrationMapper;
    private final PatientFamilyMemberMapper familyMemberMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final PatientMapper patientMapper;
    private final RegistrationRedisService redisService;
    private final ScheduleRedisService scheduleRedisService;
    private final ObjectMapper objectMapper;
    private final ConsultationMapper consultationMapper;

    @Value("${smartmed.jwt.secret}")
    private String jwtSecret;

    // ==================== 创建草稿 ====================

    public RegistrationDraftResponse createDraft(Long patientId, RegistrationDraftRequest req) {
        Long scheduleId = req.getScheduleId();
        Long familyMemberId = req.getFamilyMemberId();

        // 1. 校验排班有效性
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(404, "排班不存在");
        }
        if (!"PUBLISHED".equals(schedule.getStatus())) {
            throw new BusinessException(400, "该排班已停诊，无法挂号");
        }

        // 2. 日期/时段未过期
        validateNotExpired(schedule);

        // 3. 乐观校验号源
        int remaining = redisService.getRemainingSlots(scheduleId);
        if (remaining == -1) {
            // Redis miss，回查 PG 回填
            remaining = schedule.getRemainingSlots();
            scheduleRedisService.syncSlots(scheduleId, remaining);
        }
        if (remaining <= 0) {
            throw new BusinessException(400, "号源已抢完");
        }

        // 4. 重复挂号校验
        checkDuplicateRegistration(patientId, familyMemberId, scheduleId);

        // 5. 家庭成员归属校验
        if (familyMemberId != null) {
            PatientFamilyMember fm = familyMemberMapper.selectById(familyMemberId);
            if (fm == null || !fm.getPatientId().equals(patientId)) {
                throw new BusinessException(400, "家庭成员不存在或不属于当前用户");
            }
        }

        // 6. 生成 confirmToken
        long createdAtMillis = System.currentTimeMillis();
        String confirmToken = redisService.generateConfirmToken(patientId, scheduleId, createdAtMillis, jwtSecret);

        // 7. 构建草稿 value 并写入 Redis
        String visitorId = redisService.buildVisitorId(familyMemberId);
        String draftKey = redisService.buildDraftKey(patientId, visitorId, scheduleId);

        Map<String, Object> draftValue = new HashMap<>();
        draftValue.put("scheduleId", scheduleId);
        draftValue.put("doctorId", schedule.getDoctorId());
        draftValue.put("departmentId", schedule.getDepartmentId());
        draftValue.put("familyMemberId", familyMemberId);
        draftValue.put("createdAt", createdAtMillis);
        draftValue.put("confirmToken", confirmToken);

        try {
            redisService.saveDraft(draftKey, objectMapper.writeValueAsString(draftValue));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("草稿序列化失败", e);
        }

        // 8. 返回
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Department dept = departmentMapper.selectById(schedule.getDepartmentId());

        return RegistrationDraftResponse.builder()
                .draftKey(draftKey)
                .confirmToken(confirmToken)
                .scheduleId(scheduleId)
                .doctorName(doctor != null ? doctor.getName() : "")
                .departmentName(dept != null ? dept.getName() : "")
                .build();
    }

    // ==================== 确认挂号 ====================

    @Transactional
    public RegistrationVO confirm(Long patientId, RegistrationConfirmRequest req) {
        Long scheduleId = req.getScheduleId();
        Long familyMemberId = req.getFamilyMemberId();
        String visitorId = redisService.buildVisitorId(familyMemberId);

        // 1. 防刷校验
        if (!redisService.tryAcquireRateLimit(patientId, visitorId, scheduleId)) {
            throw new BusinessException(400, "操作过于频繁，请5秒后重试");
        }

        // 2. 消费草稿（读取 + 删除）
        String draftKey = redisService.buildDraftKey(patientId, visitorId, scheduleId);
        String draftJson = redisService.getDraft(draftKey);
        if (draftJson == null) {
            throw new BusinessException(400, "草稿不存在或已过期，请重新挂号");
        }
        redisService.deleteDraft(draftKey);

        // 3. 验证 confirmToken
        Map<String, Object> draft;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(draftJson, Map.class);
            draft = parsed;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("草稿反序列化失败", e);
        }
        String storedToken = (String) draft.get("confirmToken");
        if (!req.getConfirmToken().equals(storedToken)) {
            throw new BusinessException(400, "无效的确认令牌");
        }

        // 4. Lua 原子扣减
        long deductResult = redisService.deductSlot(scheduleId);
        if (deductResult == -2) {
            throw new BusinessException(400, "该排班已停诊，无法挂号");
        }
        if (deductResult == -1) {
            throw new BusinessException(400, "号源已抢完");
        }

        // 5. 写 PG（失败则补偿 Redis）
        try {
            Schedule schedule = scheduleMapper.selectById(scheduleId);
            Long doctorId = schedule.getDoctorId();

            // 生成 reg_no
            long seq = registrationMapper.nextRegNoSeq();
            String regNo = "REG" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                    + String.format("%03d", seq);

            Registration reg = new Registration();
            reg.setPatientId(patientId);
            reg.setScheduleId(scheduleId);
            reg.setDoctorId(doctorId);
            reg.setFamilyMemberId(familyMemberId);
            reg.setRegNo(regNo);
            reg.setStatus("REGISTERED");
            registrationMapper.insert(reg);

            // 同事务自动创建问诊（ADR-0011，06 ticket）：WAITING，pre_diagnosis=null
            Consultation consultation = new Consultation();
            consultation.setRegistrationId(reg.getId());
            consultation.setPatientId(patientId);
            consultation.setDoctorId(doctorId);
            consultation.setStatus("WAITING");
            consultationMapper.insert(consultation);

            // 原子更新 PG remaining_slots
            scheduleMapper.decrRemaining(scheduleId);

            return toVO(reg);
        } catch (Exception e) {
            // 补偿 Redis
            redisService.compensateSlot(scheduleId);
            log.error("挂号写 PG 失败，已补偿 Redis scheduleId={}", scheduleId, e);
            throw new BusinessException(500, "挂号失败，请重试");
        }
    }

    // ==================== 取消挂号 ====================

    @Transactional
    public RegistrationVO cancel(Long patientId, Long registrationId) {
        Registration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (!reg.getPatientId().equals(patientId)) {
            throw new BusinessException(403, "无权操作此挂号记录");
        }
        if (!"REGISTERED".equals(reg.getStatus())) {
            throw new BusinessException(400, "当前状态不可取消");
        }

        // 校验取消时间：就诊前 2h 以上
        Schedule schedule = scheduleMapper.selectById(reg.getScheduleId());
        if (schedule != null) {
            TimePeriod tp = TimePeriod.valueOf(schedule.getTimePeriod());
            LocalDateTime visitStart = LocalDateTime.of(schedule.getScheduleDate(), tp.getStartTime());
            LocalDateTime cancelDeadline = visitStart.minusHours(2);
            if (LocalDateTime.now().isAfter(cancelDeadline)) {
                throw new BusinessException(400, "距就诊不足2小时，无法取消");
            }
        }

        // 更新状态
        reg.setStatus("CANCELLED");
        registrationMapper.updateById(reg);

        // 释放号源：PG 原子 +1
        if (schedule != null) {
            scheduleMapper.incrRemaining(reg.getScheduleId());

            // Redis：仅 PUBLISHED 时 INCR（停诊时 key 已 DEL，恢复时从 DB 读）
            if ("PUBLISHED".equals(schedule.getStatus())) {
                redisService.releaseSlot(reg.getScheduleId());
            }
        }

        return toVO(reg);
    }

    // ==================== 查询 ====================

    public PageResponse<RegistrationVO> page(Long patientId, long pageNum, long pageSize, String status) {
        Page<Registration> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Registration> qw = new LambdaQueryWrapper<Registration>()
                .eq(Registration::getPatientId, patientId)
                .eq(status != null && !status.isBlank(), Registration::getStatus, status)
                .orderByDesc(Registration::getCreatedAt);
        Page<Registration> result = registrationMapper.selectPage(page, qw);

        var voList = result.getRecords().stream().map(this::toVO).toList();
        return new PageResponse<>(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public RegistrationVO getById(Long patientId, Long id) {
        Registration reg = registrationMapper.selectById(id);
        if (reg == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }
        if (!reg.getPatientId().equals(patientId)) {
            throw new BusinessException(403, "无权查看此挂号记录");
        }
        return toVO(reg);
    }

    // ==================== 内部方法 ====================

    private void validateNotExpired(Schedule schedule) {
        LocalDate today = LocalDate.now();
        if (schedule.getScheduleDate().isBefore(today)) {
            throw new BusinessException(400, "排班日期已过期");
        }
        if (schedule.getScheduleDate().equals(today)) {
            TimePeriod tp = TimePeriod.valueOf(schedule.getTimePeriod());
            if (LocalTime.now().isAfter(tp.getEndTime())) {
                throw new BusinessException(400, "该班次已结束，无法挂号");
            }
        }
    }

    private void checkDuplicateRegistration(Long patientId, Long familyMemberId, Long scheduleId) {
        LambdaQueryWrapper<Registration> qw = new LambdaQueryWrapper<Registration>()
                .eq(Registration::getScheduleId, scheduleId)
                .in(Registration::getStatus, "REGISTERED", "VISITED");

        if (familyMemberId == null) {
            // 本人：patient_id = 当前用户 且 family_member_id IS NULL
            qw.eq(Registration::getPatientId, patientId)
              .isNull(Registration::getFamilyMemberId);
        } else {
            // 家人：family_member_id = 指定成员
            qw.eq(Registration::getFamilyMemberId, familyMemberId);
        }

        Long count = registrationMapper.selectCount(qw);
        if (count > 0) {
            throw new BusinessException(400, "该就诊人已挂过此号，不可重复挂号");
        }
    }

    private RegistrationVO toVO(Registration reg) {
        Schedule schedule = scheduleMapper.selectById(reg.getScheduleId());
        Doctor doctor = reg.getDoctorId() != null ? doctorMapper.selectById(reg.getDoctorId()) : null;
        Department dept = schedule != null ? departmentMapper.selectById(schedule.getDepartmentId()) : null;

        // 实际就诊人姓名
        String visitorName;
        if (reg.getFamilyMemberId() != null) {
            PatientFamilyMember fm = familyMemberMapper.selectById(reg.getFamilyMemberId());
            visitorName = fm != null ? fm.getName() : "未知";
        } else {
            Patient patient = patientMapper.selectById(reg.getPatientId());
            visitorName = patient != null ? patient.getName() : "未知";
        }

        return RegistrationVO.builder()
                .id(reg.getId())
                .regNo(reg.getRegNo())
                .patientId(reg.getPatientId())
                .familyMemberId(reg.getFamilyMemberId())
                .visitorName(visitorName)
                .scheduleId(reg.getScheduleId())
                .doctorId(reg.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : "")
                .departmentName(dept != null ? dept.getName() : "")
                .scheduleDate(schedule != null ? schedule.getScheduleDate() : null)
                .timePeriod(schedule != null ? schedule.getTimePeriod() : null)
                .status(reg.getStatus())
                .createdAt(reg.getCreatedAt())
                .build();
    }
}
