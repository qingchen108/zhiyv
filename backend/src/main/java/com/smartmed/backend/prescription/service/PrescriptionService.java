package com.smartmed.backend.prescription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.common.PageResponse;
import com.smartmed.backend.consultation.entity.Consultation;
import com.smartmed.backend.consultation.mapper.ConsultationMapper;
import com.smartmed.backend.drug.entity.Drug;
import com.smartmed.backend.drug.mapper.DrugMapper;
import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.knowledge.Neo4jContraindicationService;
import com.smartmed.backend.knowledge.dto.ContraindicationWarning;
import com.smartmed.backend.prescription.dto.PrescriptionCreateResponse;
import com.smartmed.backend.prescription.dto.PrescriptionRequest;
import com.smartmed.backend.prescription.dto.PrescriptionTemplateRequest;
import com.smartmed.backend.prescription.dto.PrescriptionTemplateVO;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.prescription.entity.Prescription;
import com.smartmed.backend.prescription.entity.PrescriptionItem;
import com.smartmed.backend.prescription.entity.PrescriptionTemplate;
import com.smartmed.backend.prescription.mapper.PrescriptionItemMapper;
import com.smartmed.backend.prescription.mapper.PrescriptionMapper;
import com.smartmed.backend.prescription.mapper.PrescriptionTemplateMapper;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import com.smartmed.backend.registration.entity.Registration;
import com.smartmed.backend.registration.mapper.PatientFamilyMemberMapper;
import com.smartmed.backend.registration.mapper.RegistrationMapper;
import com.smartmed.backend.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处方业务服务（06 ticket，CONTEXT §10，ADR-0012）。
 * <p>
 * 开方：仅 consultation IN_PROGRESS/COMPLETED 可开方（WAITING 不可）；保存即 ACTIVE；禁忌检测不阻断。
 * 模板：医生个人模板，跨医生 403。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper itemMapper;
    private final PrescriptionTemplateMapper templateMapper;
    private final ConsultationMapper consultationMapper;
    private final RegistrationMapper registrationMapper;
    private final PatientMapper patientMapper;
    private final PatientFamilyMemberMapper familyMemberMapper;
    private final DrugMapper drugMapper;
    private final Neo4jContraindicationService contraindicationService;
    private final ObjectMapper objectMapper;

    // ==================== 开方 ====================

    @Transactional
    public PrescriptionCreateResponse create(PrescriptionRequest req) {
        Long doctorId = currentDoctorId();

        // 1. 校验问诊归属 + 状态（仅 IN_PROGRESS/COMPLETED 可开方）
        Consultation c = consultationMapper.selectById(req.getConsultationId());
        if (c == null) {
            throw new BusinessException(404, "问诊不存在");
        }
        if (!c.getDoctorId().equals(doctorId)) {
            throw new BusinessException(403, "无权操作此问诊");
        }
        if ("WAITING".equals(c.getStatus())) {
            throw new BusinessException(400, "待接诊问诊不可开方");
        }

        // 2. 取实际就诊人过敏史（ADR-0010 口径）
        Registration reg = registrationMapper.selectById(c.getRegistrationId());
        String allergyHistory = resolveAllergyHistory(reg);

        // 3. 取处方药品名列表（供 Neo4j 检测）
        List<Long> drugIds = req.getItems().stream().map(PrescriptionRequest.ItemRequest::getDrugId).toList();
        Map<Long, String> drugNameById = loadDrugNames(drugIds);
        List<String> drugNames = req.getItems().stream()
                .map(i -> drugNameById.get(i.getDrugId()))
                .filter(n -> n != null)
                .toList();

        // 4. 禁忌检测（不阻断，ADR-0012）
        List<ContraindicationWarning> warnings = contraindicationService.detect(drugNames, allergyHistory);

        // 5. force 审计日志（有冲突且 force=true 时记录）
        if (Boolean.TRUE.equals(req.getForce()) && !warnings.isEmpty()) {
            log.warn("医生 doctorId={} 强制开方，确认知晓 {} 条禁忌冲突：{}",
                    doctorId, warnings.size(),
                    warnings.stream().map(ContraindicationWarning::getDescription).toList());
        }

        // 6. 保存处方 + 明细（保存即 ACTIVE）
        Prescription p = new Prescription();
        p.setConsultationId(req.getConsultationId());
        p.setPatientId(c.getPatientId());
        p.setDoctorId(doctorId);
        p.setDiagnosis(req.getDiagnosis());
        p.setAdvice(req.getAdvice());
        p.setStatus("ACTIVE");
        prescriptionMapper.insert(p);

        for (PrescriptionRequest.ItemRequest item : req.getItems()) {
            PrescriptionItem pi = new PrescriptionItem();
            pi.setPrescriptionId(p.getId());
            pi.setDrugId(item.getDrugId());
            pi.setUsageMethod(item.getUsageMethod());
            pi.setDosage(item.getDosage());
            pi.setFrequency(item.getFrequency());
            pi.setRemark(item.getRemark());
            itemMapper.insert(pi);
        }

        return PrescriptionCreateResponse.builder()
                .prescription(toVO(p, req.getItems(), drugNameById))
                .warnings(warnings)
                .build();
    }

    /** 处方详情（含明细 + 药品名）。 */
    public PrescriptionVO getById(Long id) {
        Long doctorId = currentDoctorId();
        Prescription p = prescriptionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "处方不存在");
        }
        if (!p.getDoctorId().equals(doctorId)) {
            throw new BusinessException(403, "无权查看此处方");
        }
        List<PrescriptionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>()
                        .eq(PrescriptionItem::getPrescriptionId, id));
        Map<Long, String> drugNameById = loadDrugNames(items.stream().map(PrescriptionItem::getDrugId).toList());
        return toVOFromEntity(p, items, drugNameById);
    }

    // ==================== 处方模板 CRUD ====================

    public PageResponse<PrescriptionTemplateVO> pageTemplates(long pageNum, long pageSize) {
        Long doctorId = currentDoctorId();
        Page<PrescriptionTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PrescriptionTemplate> qw = new LambdaQueryWrapper<PrescriptionTemplate>()
                .eq(PrescriptionTemplate::getDoctorId, doctorId)
                .orderByDesc(PrescriptionTemplate::getUpdatedAt);
        templateMapper.selectPage(page, qw);
        return PageResponse.of(page.convert(this::toTemplateVO));
    }

    @Transactional
    public PrescriptionTemplateVO createTemplate(PrescriptionTemplateRequest req) {
        Long doctorId = currentDoctorId();
        PrescriptionTemplate t = new PrescriptionTemplate();
        t.setDoctorId(doctorId);
        t.setName(req.getName());
        t.setApplicableDiagnosis(req.getApplicableDiagnosis());
        t.setContent(serializeContent(req.getItems(), req.getAdvice()));
        templateMapper.insert(t);
        return toTemplateVO(t);
    }

    @Transactional
    public PrescriptionTemplateVO updateTemplate(Long id, PrescriptionTemplateRequest req) {
        Long doctorId = currentDoctorId();
        PrescriptionTemplate t = loadOwnedTemplate(id, doctorId);
        t.setName(req.getName());
        t.setApplicableDiagnosis(req.getApplicableDiagnosis());
        t.setContent(serializeContent(req.getItems(), req.getAdvice()));
        templateMapper.updateById(t);
        return toTemplateVO(t);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        Long doctorId = currentDoctorId();
        loadOwnedTemplate(id, doctorId);
        templateMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    private Long currentDoctorId() {
        Long doctorId = SecurityUtil.current().getDoctorId();
        if (doctorId == null) {
            throw new BusinessException(403, "当前账号未关联医生");
        }
        return doctorId;
    }

    /** 加载模板并校验归属（doctor_id = 当前医生），跨医生 403。 */
    private PrescriptionTemplate loadOwnedTemplate(Long id, Long doctorId) {
        PrescriptionTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new BusinessException(404, "处方模板不存在");
        }
        if (!t.getDoctorId().equals(doctorId)) {
            throw new BusinessException(403, "无权操作此处方模板");
        }
        return t;
    }

    /** 取实际就诊人过敏史（ADR-0010 口径）。 */
    private String resolveAllergyHistory(Registration reg) {
        if (reg == null) {
            return null;
        }
        if (reg.getFamilyMemberId() != null) {
            PatientFamilyMember fm = familyMemberMapper.selectById(reg.getFamilyMemberId());
            return fm != null ? fm.getAllergyHistory() : null;
        }
        Patient p = patientMapper.selectById(reg.getPatientId());
        return p != null ? p.getAllergyHistory() : null;
    }

    private Map<Long, String> loadDrugNames(List<Long> drugIds) {
        if (drugIds.isEmpty()) {
            return Map.of();
        }
        List<Drug> drugs = drugMapper.selectBatchIds(drugIds);
        Map<Long, String> map = new HashMap<>();
        for (Drug d : drugs) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private String serializeContent(List<PrescriptionRequest.ItemRequest> items, String advice) {
        Map<String, Object> content = new HashMap<>();
        content.put("items", items);
        content.put("advice", advice);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "模板内容序列化失败");
        }
    }

    private PrescriptionVO toVO(Prescription p, List<PrescriptionRequest.ItemRequest> items,
                                Map<Long, String> drugNameById) {
        return PrescriptionVO.builder()
                .id(p.getId())
                .consultationId(p.getConsultationId())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .diagnosis(p.getDiagnosis())
                .advice(p.getAdvice())
                .status(p.getStatus())
                .items(items.stream().map(i -> PrescriptionVO.ItemVO.builder()
                        .drugId(i.getDrugId())
                        .drugName(drugNameById.get(i.getDrugId()))
                        .usageMethod(i.getUsageMethod())
                        .dosage(i.getDosage())
                        .frequency(i.getFrequency())
                        .remark(i.getRemark())
                        .build()).toList())
                .createdAt(p.getCreatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private PrescriptionVO toVOFromEntity(Prescription p, List<PrescriptionItem> items,
                                          Map<Long, String> drugNameById) {
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
                        .drugName(drugNameById.get(i.getDrugId()))
                        .usageMethod(i.getUsageMethod())
                        .dosage(i.getDosage())
                        .frequency(i.getFrequency())
                        .remark(i.getRemark())
                        .build()).toList())
                .createdAt(p.getCreatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private PrescriptionTemplateVO toTemplateVO(PrescriptionTemplate t) {
        String advice = null;
        List<PrescriptionRequest.ItemRequest> items = List.of();
        if (t.getContent() != null) {
            try {
                Map<String, Object> content = objectMapper.readValue(t.getContent(), Map.class);
                advice = (String) content.get("advice");
                Object itemsObj = content.get("items");
                if (itemsObj instanceof List<?> list) {
                    items = list.stream().map(o -> {
                        Map<String, Object> m = (Map<String, Object>) o;
                        PrescriptionRequest.ItemRequest item = new PrescriptionRequest.ItemRequest();
                        item.setDrugId(m.get("drugId") == null ? null : Long.valueOf(m.get("drugId").toString()));
                        item.setUsageMethod((String) m.get("usageMethod"));
                        item.setDosage((String) m.get("dosage"));
                        item.setFrequency((String) m.get("frequency"));
                        item.setRemark((String) m.get("remark"));
                        return item;
                    }).toList();
                }
            } catch (JsonProcessingException e) {
                log.warn("模板 content 反序列化失败 templateId={}", t.getId(), e);
            }
        }
        return PrescriptionTemplateVO.builder()
                .id(t.getId())
                .doctorId(t.getDoctorId())
                .name(t.getName())
                .applicableDiagnosis(t.getApplicableDiagnosis())
                .advice(advice)
                .items(items)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
