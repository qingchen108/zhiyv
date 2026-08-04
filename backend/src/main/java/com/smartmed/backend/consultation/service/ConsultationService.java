package com.smartmed.backend.consultation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.consultation.dto.ConsultationVO;
import com.smartmed.backend.consultation.dto.DiagnosisRequest;
import com.smartmed.backend.consultation.dto.MedicalRecordVO;
import com.smartmed.backend.consultation.dto.MessageRequest;
import com.smartmed.backend.consultation.dto.MessageVO;
import com.smartmed.backend.consultation.entity.Consultation;
import com.smartmed.backend.consultation.entity.ConsultationMessage;
import com.smartmed.backend.consultation.mapper.ConsultationMapper;
import com.smartmed.backend.consultation.mapper.ConsultationMessageMapper;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.prescription.entity.Prescription;
import com.smartmed.backend.prescription.entity.PrescriptionItem;
import com.smartmed.backend.prescription.mapper.PrescriptionItemMapper;
import com.smartmed.backend.prescription.mapper.PrescriptionMapper;
import com.smartmed.backend.knowledge.Neo4jContraindicationService;
import com.smartmed.backend.knowledge.dto.ContraindicationWarning;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import com.smartmed.backend.registration.entity.Registration;
import com.smartmed.backend.registration.mapper.PatientFamilyMemberMapper;
import com.smartmed.backend.registration.mapper.RegistrationMapper;
import com.smartmed.backend.schedule.entity.Schedule;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import com.smartmed.backend.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 问诊业务服务（06 ticket，ADR-0011，CONTEXT §10）。
 * <p>
 * 状态机：WAITING -> IN_PROGRESS -> COMPLETED 单向不可回退；COMPLETED 同步 registration 翻 VISITED。
 * 流转权限：仅 consultation.doctor_id = 当前医生，跨医生 403。
 * 消息：仅 IN_PROGRESS 可发，医生侧仅写 DOCTOR 消息、读全部。
 * 病历聚合：以实际就诊人为中心（ADR-0010 口径），跨医生可见。
 */
@Service
@RequiredArgsConstructor
public class ConsultationService {

    /** 预问诊摘要截取长度。 */
    private static final int BRIEF_MAX = 80;

    private final ConsultationMapper consultationMapper;
    private final ConsultationMessageMapper messageMapper;
    private final RegistrationMapper registrationMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final PatientMapper patientMapper;
    private final PatientFamilyMemberMapper familyMemberMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final com.smartmed.backend.drug.mapper.DrugMapper drugMapper;
    private final Neo4jContraindicationService contraindicationService;
    private final Clock clock;

    // ==================== 今日待接诊列表 ====================

    public PageResponse<ConsultationVO> todayWaiting(long pageNum, long pageSize) {
        Long doctorId = currentDoctorId();
        List<Consultation> all = consultationMapper.findTodayWaiting(doctorId, LocalDate.now(clock));
        long total = all.size();
        // 内存分页（一天挂号量有限）
        int from = (int) Math.min((pageNum - 1) * pageSize, total);
        int to = (int) Math.min(from + pageSize, total);
        List<ConsultationVO> records = all.subList(from, to).stream()
                .map(this::toVO)
                .toList();
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    // ==================== 问诊详情 ====================

    public ConsultationVO getById(Long id) {
        Consultation c = loadOwnedConsultation(id);
        return toVO(c);
    }

    // ==================== 状态流转 ====================

    /** 接诊：WAITING -> IN_PROGRESS。 */
    @Transactional
    public ConsultationVO start(Long id) {
        Consultation c = loadOwnedConsultation(id);
        if (!"WAITING".equals(c.getStatus())) {
            throw new BusinessException(400, "当前状态不可接诊（仅 WAITING 可接诊）");
        }
        c.setStatus("IN_PROGRESS");
        consultationMapper.updateById(c);
        return toVO(c);
    }

    /** 完成：IN_PROGRESS -> COMPLETED，同步 registration 翻 VISITED。 */
    @Transactional
    public ConsultationVO complete(Long id) {
        Consultation c = loadOwnedConsultation(id);
        if (!"IN_PROGRESS".equals(c.getStatus())) {
            throw new BusinessException(400, "当前状态不可完成（仅 IN_PROGRESS 可完成）");
        }
        c.setStatus("COMPLETED");
        consultationMapper.updateById(c);

        // 同步 registration -> VISITED（ADR-0011）
        Registration reg = registrationMapper.selectById(c.getRegistrationId());
        if (reg != null && "REGISTERED".equals(reg.getStatus())) {
            reg.setStatus("VISITED");
            registrationMapper.updateById(reg);
        }
        return toVO(c);
    }

    // ==================== 诊断保存 ====================

    /** 保存诊断：IN_PROGRESS 可改。 */
    @Transactional
    public ConsultationVO saveDiagnosis(Long id, DiagnosisRequest req) {
        Consultation c = loadOwnedConsultation(id);
        if (!"IN_PROGRESS".equals(c.getStatus())) {
            throw new BusinessException(400, "仅进行中问诊可保存诊断");
        }
        c.setDiagnosis(req.getDiagnosis());
        consultationMapper.updateById(c);
        return toVO(c);
    }

    // ==================== 消息 ====================

    /** 发消息：仅 DOCTOR、仅 IN_PROGRESS。 */
    @Transactional
    public MessageVO sendMessage(Long id, MessageRequest req) {
        Consultation c = loadOwnedConsultation(id);
        if (!"IN_PROGRESS".equals(c.getStatus())) {
            throw new BusinessException(400, "仅进行中问诊可发送消息");
        }
        ConsultationMessage msg = new ConsultationMessage();
        msg.setConsultationId(id);
        msg.setSenderType("DOCTOR");
        msg.setContent(req.getContent());
        messageMapper.insert(msg);
        return MessageVO.builder()
                .id(msg.getId())
                .senderType(msg.getSenderType())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    /** 消息列表：读全部（DOCTOR + PATIENT），按时间升序。 */
    public List<MessageVO> listMessages(Long id) {
        loadOwnedConsultation(id); // 校验归属
        List<ConsultationMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<ConsultationMessage>()
                        .eq(ConsultationMessage::getConsultationId, id)
                        .orderByAsc(ConsultationMessage::getCreatedAt));
        return msgs.stream().map(m -> MessageVO.builder()
                .id(m.getId())
                .senderType(m.getSenderType())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build()).toList();
    }

    // ==================== 病历聚合 ====================

    public MedicalRecordVO medicalRecord(Long id) {
        Consultation c = loadOwnedConsultation(id);
        Registration reg = registrationMapper.selectById(c.getRegistrationId());
        if (reg == null) {
            throw new BusinessException(404, "挂号记录不存在");
        }

        // 实际就诊人基本信息（ADR-0010 口径）
        Long familyMemberId = reg.getFamilyMemberId();
        String visitorName;
        String visitorGender;
        LocalDate visitorBirthDate;
        String allergyHistory;

        if (familyMemberId != null) {
            PatientFamilyMember fm = familyMemberMapper.selectById(familyMemberId);
            if (fm == null) {
                throw new BusinessException(404, "家庭成员不存在");
            }
            visitorName = fm.getName();
            visitorGender = fm.getGender();
            visitorBirthDate = fm.getBirthDate();
            allergyHistory = fm.getAllergyHistory();
        } else {
            Patient p = patientMapper.selectById(reg.getPatientId());
            if (p == null) {
                throw new BusinessException(404, "患者不存在");
            }
            visitorName = p.getName();
            visitorGender = p.getGender();
            visitorBirthDate = p.getBirthDate();
            allergyHistory = p.getAllergyHistory();
        }

        // 历史挂号（实际就诊人维度，ADR-0010）
        List<Registration> registrations = familyMemberId != null
                ? consultationMapper.findRegistrationsByFamilyMember(familyMemberId)
                : consultationMapper.findRegistrationsBySelf(reg.getPatientId());

        // 历史问诊（通过挂号 ID 关联）
        List<Long> regIds = registrations.stream().map(Registration::getId).toList();
        List<Consultation> consultations = regIds.isEmpty() ? List.of()
                : consultationMapper.selectList(new LambdaQueryWrapper<Consultation>()
                        .in(Consultation::getRegistrationId, regIds)
                        .orderByDesc(Consultation::getCreatedAt));

        // 历史处方（通过问诊 ID 关联）
        List<Long> consultationIds = consultations.stream().map(Consultation::getId).toList();
        List<Prescription> prescriptions = consultationIds.isEmpty() ? List.of()
                : prescriptionMapper.selectList(new LambdaQueryWrapper<Prescription>()
                        .in(Prescription::getConsultationId, consultationIds)
                        .orderByDesc(Prescription::getCreatedAt));

        // 处方明细批量查（避免 N+1）
        Map<Long, List<PrescriptionItem>> itemsByPrescription = loadItemsByPrescription(
                prescriptions.stream().map(Prescription::getId).toList());

        // 医生/科室名批量加载
        Map<Long, String> doctorNames = loadDoctorNames(registrations.stream()
                .map(Registration::getDoctorId).distinct().toList());

        return MedicalRecordVO.builder()
                .visitorName(visitorName)
                .visitorGender(visitorGender)
                .visitorAge(computeAge(visitorBirthDate))
                .allergyHistory(allergyHistory)
                .registrations(registrations.stream().map(r -> toRegistrationSummary(r, doctorNames)).toList())
                .consultations(consultations.stream().map(co -> toConsultationSummary(co, doctorNames)).toList())
                .prescriptions(prescriptions.stream()
                        .map(p -> toPrescriptionVO(p, itemsByPrescription.getOrDefault(p.getId(), List.of())))
                        .toList())
                .build();
    }

    // ==================== 该问诊的处方列表（供 /consultations/{id}/prescriptions） ====================

    public List<PrescriptionVO> listPrescriptionsByConsultation(Long id) {
        loadOwnedConsultation(id);
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getConsultationId, id)
                        .orderByDesc(Prescription::getCreatedAt));
        Map<Long, List<PrescriptionItem>> itemsByPrescription = loadItemsByPrescription(
                prescriptions.stream().map(Prescription::getId).toList());
        return prescriptions.stream()
                .map(p -> toPrescriptionVO(p, itemsByPrescription.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    // ==================== 内部方法 ====================

    /** 取当前登录医生 ID，非医生或未关联 403。 */
    private Long currentDoctorId() {
        Long doctorId = SecurityUtil.current().getDoctorId();
        if (doctorId == null) {
            throw new BusinessException(403, "当前账号未关联医生");
        }
        return doctorId;
    }

    /** 加载问诊并校验归属（doctor_id = 当前医生），跨医生 403。 */
    private Consultation loadOwnedConsultation(Long id) {
        Consultation c = consultationMapper.selectById(id);
        if (c == null) {
            throw new BusinessException(404, "问诊不存在");
        }
        Long doctorId = currentDoctorId();
        if (!c.getDoctorId().equals(doctorId)) {
            throw new BusinessException(403, "无权操作此问诊");
        }
        return c;
    }

    /**
     * 构建问诊 VO（详情 + 列表共用）。
     * <p>public 供 C 端记录查询（08 ticket RecordsService）复用，避免重复实现。
     */
    public ConsultationVO toVO(Consultation c) {
        Registration reg = registrationMapper.selectById(c.getRegistrationId());
        Schedule schedule = reg != null ? scheduleMapper.selectById(reg.getScheduleId()) : null;
        Doctor doctor = doctorMapper.selectById(c.getDoctorId());
        Department dept = schedule != null ? departmentMapper.selectById(schedule.getDepartmentId()) : null;

        // 实际就诊人信息
        String visitorName = null;
        String visitorGender = null;
        LocalDate visitorBirthDate = null;
        if (reg != null) {
            if (reg.getFamilyMemberId() != null) {
                PatientFamilyMember fm = familyMemberMapper.selectById(reg.getFamilyMemberId());
                if (fm != null) {
                    visitorName = fm.getName();
                    visitorGender = fm.getGender();
                    visitorBirthDate = fm.getBirthDate();
                }
            } else {
                Patient p = patientMapper.selectById(reg.getPatientId());
                if (p != null) {
                    visitorName = p.getName();
                    visitorGender = p.getGender();
                    visitorBirthDate = p.getBirthDate();
                }
            }
        }

        String brief = c.getPreDiagnosis() == null ? null
                : (c.getPreDiagnosis().length() <= BRIEF_MAX
                        ? c.getPreDiagnosis()
                        : c.getPreDiagnosis().substring(0, BRIEF_MAX));

        return ConsultationVO.builder()
                .id(c.getId())
                .registrationId(c.getRegistrationId())
                .patientId(c.getPatientId())
                .doctorId(c.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .departmentName(dept != null ? dept.getName() : null)
                .regNo(reg != null ? reg.getRegNo() : null)
                .scheduleDate(schedule != null ? schedule.getScheduleDate() : null)
                .timePeriod(schedule != null ? schedule.getTimePeriod() : null)
                .visitorName(visitorName)
                .visitorGender(visitorGender)
                .visitorAge(computeAge(visitorBirthDate))
                .preDiagnosis(c.getPreDiagnosis())
                .preDiagnosisBrief(brief)
                .diagnosis(c.getDiagnosis())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private Integer computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private Map<Long, List<PrescriptionItem>> loadItemsByPrescription(List<Long> prescriptionIds) {
        if (prescriptionIds.isEmpty()) {
            return Map.of();
        }
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .in(PrescriptionItem::getPrescriptionId, prescriptionIds));
        Map<Long, List<PrescriptionItem>> map = new HashMap<>();
        for (PrescriptionItem item : items) {
            map.computeIfAbsent(item.getPrescriptionId(), k -> new java.util.ArrayList<>()).add(item);
        }
        return map;
    }

    private Map<Long, String> loadDoctorNames(List<Long> doctorIds) {
        if (doctorIds.isEmpty()) {
            return Map.of();
        }
        List<Doctor> doctors = doctorMapper.selectBatchIds(doctorIds);
        Map<Long, String> map = new HashMap<>();
        for (Doctor d : doctors) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    /** 批量取药品名（处方明细展示用，避免前端降级显示"药品{id}"）。 */
    private Map<Long, String> loadDrugNames(List<Long> drugIds) {
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        List<com.smartmed.backend.drug.entity.Drug> drugs = drugMapper.selectBatchIds(drugIds);
        Map<Long, String> map = new HashMap<>();
        for (com.smartmed.backend.drug.entity.Drug d : drugs) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private MedicalRecordVO.RegistrationSummary toRegistrationSummary(Registration r, Map<Long, String> doctorNames) {
        Schedule schedule = scheduleMapper.selectById(r.getScheduleId());
        Department dept = schedule != null ? departmentMapper.selectById(schedule.getDepartmentId()) : null;
        return MedicalRecordVO.RegistrationSummary.builder()
                .id(r.getId())
                .regNo(r.getRegNo())
                .doctorName(doctorNames.get(r.getDoctorId()))
                .departmentName(dept != null ? dept.getName() : null)
                .scheduleDate(schedule != null ? schedule.getScheduleDate() : null)
                .timePeriod(schedule != null ? schedule.getTimePeriod() : null)
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private MedicalRecordVO.ConsultationSummary toConsultationSummary(Consultation c, Map<Long, String> doctorNames) {
        return MedicalRecordVO.ConsultationSummary.builder()
                .id(c.getId())
                .doctorName(doctorNames.get(c.getDoctorId()))
                .diagnosis(c.getDiagnosis())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private PrescriptionVO toPrescriptionVO(Prescription p, List<PrescriptionItem> items) {
        // 批量加载药品名（单处方的 items 数量有限，避免前端降级显示"药品{id}"）
        Map<Long, String> drugNames = loadDrugNames(items.stream().map(PrescriptionItem::getDrugId).distinct().toList());
        return PrescriptionVO.builder()
                .id(p.getId())
                .consultationId(p.getConsultationId())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .diagnosis(p.getDiagnosis())
                .advice(p.getAdvice())
                .status(p.getStatus())
                .items(items.stream().map(i -> PrescriptionVO.ItemVO.builder()
                        .id(i.getId())
                        .drugId(i.getDrugId())
                        .drugName(drugNames.get(i.getDrugId()))
                        .usageMethod(i.getUsageMethod())
                        .dosage(i.getDosage())
                        .frequency(i.getFrequency())
                        .remark(i.getRemark())
                        .build()).toList())
                .createdAt(p.getCreatedAt())
                .build();
    }

    // ==================== Agent 工具：预问诊摘要写入（ticket 13） ====================

    @Transactional
    public void updatePreDiagnosis(Long consultationId, String content) {
        Consultation c = consultationMapper.selectById(consultationId);
        if (c == null) {
            throw new BusinessException(404, "问诊记录不存在");
        }
        if (!"IN_PROGRESS".equals(c.getStatus())) {
            throw new BusinessException(400, "仅进行中问诊可写入预问诊摘要");
        }
        c.setPreDiagnosis(content);
        consultationMapper.updateById(c);
    }

    // ==================== Agent 工具：处方查询（ticket 13） ====================

    public PrescriptionVO getPrescriptionDetail(Long prescriptionId) {
        Prescription p = prescriptionMapper.selectById(prescriptionId);
        if (p == null) {
            throw new BusinessException(404, "处方不存在");
        }
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .eq(PrescriptionItem::getPrescriptionId, prescriptionId));
        return toPrescriptionVO(p, items);
    }

    // ==================== Agent 工具：过敏风险检测（ticket 13） ====================

    public List<ContraindicationWarning> checkAllergyForAgent(Long patientId, Long familyMemberId, List<String> drugNames) {
        String allergyHistory = resolveAllergyHistory(patientId, familyMemberId);
        return contraindicationService.detect(drugNames, allergyHistory);
    }

    private String resolveAllergyHistory(Long patientId, Long familyMemberId) {
        if (familyMemberId != null) {
            PatientFamilyMember fm = familyMemberMapper.selectById(familyMemberId);
            return fm != null ? fm.getAllergyHistory() : null;
        }
        Patient p = patientMapper.selectById(patientId);
        return p != null ? p.getAllergyHistory() : null;
    }
}
