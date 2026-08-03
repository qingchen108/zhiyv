package com.smartmed.backend.records.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.consultation.dto.ConsultationVO;
import com.smartmed.backend.consultation.dto.MedicalRecordVO;
import com.smartmed.backend.consultation.dto.MessageVO;
import com.smartmed.backend.consultation.entity.Consultation;
import com.smartmed.backend.consultation.entity.ConsultationMessage;
import com.smartmed.backend.consultation.mapper.ConsultationMapper;
import com.smartmed.backend.consultation.mapper.ConsultationMessageMapper;
import com.smartmed.backend.consultation.service.ConsultationService;
import com.smartmed.backend.department.entity.Department;
import com.smartmed.backend.department.mapper.DepartmentMapper;
import com.smartmed.backend.doctor.entity.Doctor;
import com.smartmed.backend.doctor.mapper.DoctorMapper;
import com.smartmed.backend.drug.entity.Drug;
import com.smartmed.backend.drug.mapper.DrugMapper;
import com.smartmed.backend.order.dto.DrugOrderVO;
import com.smartmed.backend.order.dto.ReminderVO;
import com.smartmed.backend.order.entity.DrugOrder;
import com.smartmed.backend.order.entity.MedicationReminder;
import com.smartmed.backend.order.entity.Pharmacy;
import com.smartmed.backend.order.mapper.DrugOrderMapper;
import com.smartmed.backend.order.mapper.MedicationReminderMapper;
import com.smartmed.backend.order.mapper.PharmacyMapper;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.prescription.entity.Prescription;
import com.smartmed.backend.prescription.entity.PrescriptionItem;
import com.smartmed.backend.prescription.mapper.PrescriptionItemMapper;
import com.smartmed.backend.prescription.mapper.PrescriptionMapper;
import com.smartmed.backend.records.mapper.RecordsMapper;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import com.smartmed.backend.registration.entity.Registration;
import com.smartmed.backend.registration.mapper.PatientFamilyMemberMapper;
import com.smartmed.backend.registration.mapper.RegistrationMapper;
import com.smartmed.backend.schedule.entity.Schedule;
import com.smartmed.backend.schedule.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端记录查询服务（08 ticket）。
 * <p>
 * 覆盖：问诊记录（列表/详情/对话/处方）、处方记录（列表/详情）、购药订单（列表/详情）、
 * 用药提醒（列表）、健康档案汇总（成员档案 + 就诊/用药汇总）。
 * <p>
 * 全部按"实际就诊人"过滤（ADR-0010 口径）：familyMemberId 为 null/0 → 本人；否则该成员。
 * 归属校验：详情/对话等单条查询校验 registration.patient_id = 当前患者（含帮家人挂的记录）。
 */
@Service
@RequiredArgsConstructor
public class RecordsService {

    private final RecordsMapper recordsMapper;
    private final ConsultationMapper consultationMapper;
    private final ConsultationMessageMapper messageMapper;
    private final ConsultationService consultationService;
    private final RegistrationMapper registrationMapper;
    private final PatientFamilyMemberMapper familyMemberMapper;
    private final PatientMapper patientMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final DrugOrderMapper drugOrderMapper;
    private final MedicationReminderMapper reminderMapper;
    private final PharmacyMapper pharmacyMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final DrugMapper drugMapper;

    // ==================== 问诊记录 ====================

    /** 问诊记录分页（按当前成员）。 */
    public PageResponse<ConsultationVO> pageConsultations(Long patientId, Long familyMemberId,
                                                          long pageNum, long pageSize) {
        validateFamilyMember(patientId, familyMemberId);
        Page<Consultation> page = new Page<>(pageNum, pageSize);
        recordsMapper.pageConsultations(page, patientId, familyMemberId);
        List<ConsultationVO> records = page.getRecords().stream()
                .map(consultationService::toVO)
                .toList();
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 问诊详情（校验归属：操作人 = 当前患者）。 */
    public ConsultationVO getConsultation(Long patientId, Long consultationId) {
        Consultation c = loadOwnedConsultation(patientId, consultationId);
        return consultationService.toVO(c);
    }

    /** 问诊对话记录（按时间升序）。 */
    public List<MessageVO> listMessages(Long patientId, Long consultationId) {
        loadOwnedConsultation(patientId, consultationId);
        List<ConsultationMessage> msgs = messageMapper.selectList(
                new LambdaQueryWrapper<ConsultationMessage>()
                        .eq(ConsultationMessage::getConsultationId, consultationId)
                        .orderByAsc(ConsultationMessage::getCreatedAt));
        return msgs.stream().map(m -> MessageVO.builder()
                .id(m.getId())
                .senderType(m.getSenderType())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build()).toList();
    }

    /** 该问诊的处方列表（详情页展示）。 */
    public List<PrescriptionVO> listConsultationPrescriptions(Long patientId, Long consultationId) {
        loadOwnedConsultation(patientId, consultationId);
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getConsultationId, consultationId)
                        .orderByDesc(Prescription::getCreatedAt));
        return prescriptions.stream().map(this::toPrescriptionVO).toList();
    }

    // ==================== 处方记录 ====================

    /** 处方分页（按当前成员）。 */
    public PageResponse<PrescriptionVO> pagePrescriptions(Long patientId, Long familyMemberId,
                                                          long pageNum, long pageSize) {
        validateFamilyMember(patientId, familyMemberId);
        Page<Prescription> page = new Page<>(pageNum, pageSize);
        recordsMapper.pagePrescriptions(page, patientId, familyMemberId);
        List<PrescriptionVO> records = page.getRecords().stream()
                .map(this::toPrescriptionVO)
                .toList();
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 处方详情（校验归属）。 */
    public PrescriptionVO getPrescription(Long patientId, Long prescriptionId) {
        Prescription p = recordsMapper.findPrescriptionByPatient(prescriptionId, patientId);
        if (p == null) {
            throw new BusinessException(404, "处方不存在");
        }
        return toPrescriptionVO(p);
    }

    // ==================== 购药订单 ====================

    /** 订单分页（按当前成员）。 */
    public PageResponse<DrugOrderVO> pageOrders(Long patientId, Long familyMemberId,
                                                long pageNum, long pageSize) {
        validateFamilyMember(patientId, familyMemberId);
        Page<DrugOrder> page = new Page<>(pageNum, pageSize);
        recordsMapper.pageOrders(page, patientId, familyMemberId);
        List<DrugOrderVO> records = page.getRecords().stream()
                .map(this::toOrderVO)
                .toList();
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 订单详情（校验归属）。 */
    public DrugOrderVO getOrder(Long patientId, Long orderId) {
        DrugOrder order = recordsMapper.findOrderByPatient(orderId, patientId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return toOrderVO(order);
    }

    // ==================== 用药提醒 ====================

    /** 提醒列表（按当前成员，下次提醒时间升序）。 */
    public List<ReminderVO> listReminders(Long patientId, Long familyMemberId) {
        validateFamilyMember(patientId, familyMemberId);
        List<MedicationReminder> reminders = recordsMapper.listReminders(patientId, familyMemberId);
        return reminders.stream().map(this::toReminderVO).toList();
    }

    // ==================== 健康档案汇总 ====================

    /** 成员档案汇总：基本信息 + 就诊汇总（挂号/问诊/处方）+ 用药汇总（订单/提醒）。 */
    public MedicalRecordVO getHealthProfile(Long patientId, Long familyMemberId) {
        validateFamilyMember(patientId, familyMemberId);

        // 实际就诊人基本信息（ADR-0010 口径）
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
            Patient p = patientMapper.selectById(patientId);
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
                : consultationMapper.findRegistrationsBySelf(patientId);

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

        // 购药订单 + 用药提醒（通过处方 ID 关联）
        List<Long> prescriptionIds = prescriptions.stream().map(Prescription::getId).toList();
        List<DrugOrder> orders = prescriptionIds.isEmpty() ? List.of()
                : drugOrderMapper.selectList(new LambdaQueryWrapper<DrugOrder>()
                        .in(DrugOrder::getPrescriptionId, prescriptionIds)
                        .orderByDesc(DrugOrder::getCreatedAt));
        List<MedicationReminder> reminders = prescriptionIds.isEmpty() ? List.of()
                : reminderMapper.selectList(new LambdaQueryWrapper<MedicationReminder>()
                        .in(MedicationReminder::getPrescriptionId, prescriptionIds)
                        .orderByAsc(MedicationReminder::getNextRemindAt));

        // 医生/科室名批量加载
        Map<Long, String> doctorNames = loadDoctorNames(registrations.stream()
                .map(Registration::getDoctorId).distinct().toList());

        return MedicalRecordVO.builder()
                .visitorName(visitorName)
                .visitorGender(visitorGender)
                .visitorAge(computeAge(visitorBirthDate))
                .allergyHistory(allergyHistory)
                .registrations(registrations.stream()
                        .map(r -> toRegistrationSummary(r, doctorNames)).toList())
                .consultations(consultations.stream()
                        .map(c -> toConsultationSummary(c, doctorNames)).toList())
                .prescriptions(prescriptions.stream().map(this::toPrescriptionVO).toList())
                .orders(orders.stream().map(this::toOrderVO).toList())
                .reminders(reminders.stream().map(this::toReminderVO).toList())
                .build();
    }

    // ==================== 内部方法 ====================

    /** 校验成员归属：familyMemberId 非空时必须属于当前患者（防越权）。 */
    private void validateFamilyMember(Long patientId, Long familyMemberId) {
        if (familyMemberId == null) {
            return;
        }
        PatientFamilyMember fm = familyMemberMapper.selectById(familyMemberId);
        if (fm == null || !fm.getPatientId().equals(patientId)) {
            throw new BusinessException(403, "无权访问该成员的记录");
        }
    }

    /** 加载问诊并校验 C 端归属（registration.patient_id = 当前患者）。 */
    private Consultation loadOwnedConsultation(Long patientId, Long consultationId) {
        Consultation c = recordsMapper.findConsultationByPatient(consultationId, patientId);
        if (c == null) {
            throw new BusinessException(404, "问诊不存在");
        }
        return c;
    }

    /** 处方 VO（含明细 + 药品名 + 开方医生姓名）。 */
    private PrescriptionVO toPrescriptionVO(Prescription p) {
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .eq(PrescriptionItem::getPrescriptionId, p.getId()));
        Map<Long, String> drugNames = loadDrugNames(items.stream()
                .map(PrescriptionItem::getDrugId).distinct().toList());
        Doctor doctor = p.getDoctorId() != null ? doctorMapper.selectById(p.getDoctorId()) : null;
        return PrescriptionVO.builder()
                .id(p.getId())
                .consultationId(p.getConsultationId())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .doctorName(doctor != null ? doctor.getName() : null)
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

    /** 订单 VO（含药店名称/地址）。 */
    private DrugOrderVO toOrderVO(DrugOrder order) {
        Pharmacy pharmacy = order.getPharmacyId() != null ? pharmacyMapper.selectById(order.getPharmacyId()) : null;
        return DrugOrderVO.builder()
                .id(order.getId())
                .patientId(order.getPatientId())
                .prescriptionId(order.getPrescriptionId())
                .pharmacyId(order.getPharmacyId())
                .pharmacyName(pharmacy != null ? pharmacy.getName() : null)
                .pharmacyAddress(pharmacy != null ? pharmacy.getAddress() : null)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .deliveryInfo(order.getDeliveryInfo())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /** 提醒 VO（含药品名）。 */
    private ReminderVO toReminderVO(MedicationReminder r) {
        Drug drug = r.getDrugId() != null ? drugMapper.selectById(r.getDrugId()) : null;
        return ReminderVO.builder()
                .id(r.getId())
                .prescriptionId(r.getPrescriptionId())
                .drugId(r.getDrugId())
                .drugName(drug != null ? drug.getName() : null)
                .nextRemindAt(r.getNextRemindAt())
                .frequency(r.getFrequency())
                .dosage(r.getDosage())
                .remark(r.getRemark())
                .status(r.getStatus())
                .build();
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

    private Map<Long, String> loadDoctorNames(List<Long> doctorIds) {
        if (doctorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Doctor d : doctorMapper.selectBatchIds(doctorIds)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private Map<Long, String> loadDrugNames(List<Long> drugIds) {
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Drug d : drugMapper.selectBatchIds(drugIds)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private Integer computeAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
