package com.smartmed.backend.agent.service;

import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.consultation.service.ConsultationService;
import com.smartmed.backend.department.service.DepartmentService;
import com.smartmed.backend.doctor.service.DoctorService;
import com.smartmed.backend.drug.mapper.DrugPharmacyStockMapper;
import com.smartmed.backend.knowledge.KnowledgeGraphService;
import com.smartmed.backend.knowledge.Neo4jContraindicationService;
import com.smartmed.backend.knowledge.dto.ContraindicationWarning;
import com.smartmed.backend.order.service.OrderService;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import com.smartmed.backend.records.service.RecordsService;
import com.smartmed.backend.registration.dto.RegistrationDraftRequest;
import com.smartmed.backend.registration.service.RegistrationService;
import com.smartmed.backend.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 工具分发器（09 ticket，ADR-0015）。
 * <p>
 * 工具契约单一来源是 agent/tools/tools.json（11 个工具，查询 7 + 动作 4），
 * Java 侧按名分发：未知工具 404；已知工具 09 阶段一律 501；11+ 逐一接入真实 handler。
 * <p>
 * 实现原则（ADR-0016）：Agent handler 委托给现有 Service 层，不直接操作 Mapper/DB。
 */
@Component
@RequiredArgsConstructor
public class AgentToolDispatcher {

    /**
     * 契约工具全集（11 个）—— agent/tools/tools.json 的静态镜像。
     * <p>
     * 工具契约唯一来源是 tools.json（Python 启动读入注册），Java 侧不跨目录读该文件，
     * 此处按契约同步镜像；若契约新增工具需同步本表。
     */
    private static final Set<String> KNOWN_TOOLS = Set.of(
            // 查询类（7）
            "query_departments", "query_doctors", "query_schedule",
            "query_knowledge_graph", "get_medical_record", "get_prescription",
            "query_pharmacy_stock",
            // 动作类（4）
            "create_registration_draft", "write_pre_diagnosis", "check_allergy",
            "create_order_draft", "create_reminder"
    );

    private final KnowledgeGraphService knowledgeGraphService;
    private final DepartmentService departmentService;
    private final DoctorService doctorService;
    private final ScheduleService scheduleService;
    private final RegistrationService registrationService;
    private final ConsultationService consultationService;
    private final DrugPharmacyStockMapper stockMapper;
    private final OrderService orderService;
    private final RecordsService recordsService;

    /**
     * 分发工具调用。
     *
     * @param toolName  工具名（蛇形）
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public Object dispatch(String toolName, Map<String, Object> arguments, String patientId) {
        if (!KNOWN_TOOLS.contains(toolName)) {
            throw new BusinessException(404, "未知工具: " + toolName);
        }

        return switch (toolName) {
            // === 11 ticket 导诊相关工具 ===
            case "query_knowledge_graph" -> dispatchQueryKnowledgeGraph(arguments);
            case "query_doctors" -> dispatchQueryDoctors(arguments);
            case "query_departments" -> dispatchQueryDepartments(arguments);

            // === 12 ticket 挂号相关工具 ===
            case "query_schedule" -> dispatchQuerySchedule(arguments);
            case "create_registration_draft" -> dispatchCreateRegistrationDraft(arguments, patientId);

            // === 13 ticket 预问诊与处方解读 ===
            case "write_pre_diagnosis" -> dispatchWritePreDiagnosis(arguments);
            case "get_prescription" -> dispatchGetPrescription(arguments);
            case "get_medical_record" -> dispatchGetMedicalRecord(arguments, patientId);
            case "check_allergy" -> dispatchCheckAllergy(arguments, patientId);

            // === 14 ticket 购药相关工具 ===
            case "query_pharmacy_stock" -> dispatchQueryPharmacyStock(arguments);
            case "create_order_draft" -> dispatchCreateOrderDraft(arguments, patientId);

            // create_reminder 降级为手动补充，暂不实现
            default -> throw new BusinessException(501, "工具未实现: " + toolName);
        };
    }

    // ==================== 知识图谱查询 ====================

    /** 知识图谱查询：症状 -> 疾病 -> 科室。 */
    private Object dispatchQueryKnowledgeGraph(Map<String, Object> args) {
        String keyword = extractString(args, "keyword");
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(400, "keyword 不能为空");
        }
        return Map.of("results", knowledgeGraphService.query(keyword));
    }

    // ==================== 医生查询 ====================

    /** 医生查询：按科室筛选，返回医生+号源推荐。 */
    private Object dispatchQueryDoctors(Map<String, Object> args) {
        Long departmentId = extractLong(args, "department_id");
        if (departmentId == null) {
            throw new BusinessException(400, "department_id 不能为空");
        }
        return Map.of("doctors", doctorService.queryForTriage(departmentId));
    }

    // ==================== 科室查询 ====================

    /** 科室查询：按名称模糊搜索，返回全部或匹配。 */
    private Object dispatchQueryDepartments(Map<String, Object> args) {
        String name = extractString(args, "name");
        return Map.of("departments", departmentService.page(1, 100, name));
    }

    // ==================== 排班查询 ====================

    /** 排班查询：按医生/科室/日期筛选，返回扁平列表，过滤 SUSPENDED 和余量 0。 */
    private Object dispatchQuerySchedule(Map<String, Object> args) {
        Long doctorId = extractLong(args, "doctor_id");
        Long departmentId = extractLong(args, "department_id");
        String dateStr = extractString(args, "date");
        java.time.LocalDate date = dateStr != null ? java.time.LocalDate.parse(dateStr) : null;
        return Map.of("schedules", scheduleService.queryForAgent(doctorId, departmentId, date));
    }

    // ==================== 创建挂号草稿 ====================

    /** 创建挂号草稿：委托 RegistrationService.createDraft()，patientId 从 X-Patient-Id header 注入。 */
    private Object dispatchCreateRegistrationDraft(Map<String, Object> args, String patientId) {
        Long scheduleId = extractLong(args, "schedule_id");
        if (scheduleId == null) {
            throw new BusinessException(400, "schedule_id 不能为空");
        }
        Long familyMemberId = extractLong(args, "family_member_id");

        Long patientIdLong;
        try {
            patientIdLong = Long.parseLong(patientId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "X-Patient-Id 无效");
        }

        RegistrationDraftRequest draftReq = new RegistrationDraftRequest();
        draftReq.setScheduleId(scheduleId);
        draftReq.setFamilyMemberId(familyMemberId);

        return registrationService.createDraft(patientIdLong, draftReq);
    }

    // ==================== 13 ticket: 预问诊摘要写入 ====================

    /** 写入预问诊摘要到 consultation.pre_diagnosis */
    private Object dispatchWritePreDiagnosis(Map<String, Object> args) {
        Long consultationId = extractLong(args, "consultation_id");
        String content = extractString(args, "content");
        if (consultationId == null) {
            throw new BusinessException(400, "consultation_id 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "content 不能为空");
        }
        consultationService.updatePreDiagnosis(consultationId, content);
        return Map.of("success", true);
    }

    /** 处方查询（含明细） */
    private Object dispatchGetPrescription(Map<String, Object> args) {
        Long prescriptionId = extractLong(args, "prescription_id");
        if (prescriptionId == null) {
            throw new BusinessException(400, "prescription_id 不能为空");
        }
        PrescriptionVO vo = consultationService.getPrescriptionDetail(prescriptionId);
        return Map.of("prescription", vo);
    }

    /**
     * 病历聚合查询（ticket 14）：以实际就诊人为中心聚合挂号/问诊/处方/过敏史/订单/提醒。
     * <p>
     * 委托 RecordsService.getHealthProfile（C 端健康档案汇总同一实现，ADR-0016），
     * patientId 从 X-Patient-Id header 取，可选 family_member_id 切换就诊人。
     * Agent pharmacy 节点用此取最近 ACTIVE 处方。
     */
    private Object dispatchGetMedicalRecord(Map<String, Object> args, String patientId) {
        Long patientIdLong;
        try {
            patientIdLong = Long.parseLong(patientId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "X-Patient-Id 无效");
        }
        Long familyMemberId = extractLong(args, "family_member_id");
        return Map.of("record", recordsService.getHealthProfile(patientIdLong, familyMemberId));
    }

    /** 过敏风险检测：处方药物 vs 患者过敏史 */
    private Object dispatchCheckAllergy(Map<String, Object> args, String patientId) {
        Long patientIdLong;
        try {
            patientIdLong = Long.parseLong(patientId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "X-Patient-Id 无效");
        }
        Long familyMemberId = extractLong(args, "family_member_id");
        @SuppressWarnings("unchecked")
        List<String> drugNames = (List<String>) args.get("drug_names");
        if (drugNames == null || drugNames.isEmpty()) {
            throw new BusinessException(400, "drug_names 不能为空");
        }
        List<ContraindicationWarning> warnings = consultationService.checkAllergyForAgent(patientIdLong, familyMemberId, drugNames);
        boolean hasAllergy = warnings.stream().anyMatch(w -> "ALLERGY".equals(w.getType()));
        return Map.of("warnings", warnings, "has_allergy_risk", hasAllergy);
    }

    // ==================== 14 ticket: 购药相关工具 ====================

    /** 查询药店库存：按 drug_id 查询各药店库存/价格/配送时效，返回扁平列表。 */
    private Object dispatchQueryPharmacyStock(Map<String, Object> args) {
        Long drugId = extractLong(args, "drug_id");
        if (drugId == null) {
            throw new BusinessException(400, "drug_id 不能为空");
        }
        return Map.of("stocks", stockMapper.selectByDrugId(drugId));
    }

    /** 创建购药草稿：委托 OrderService.createOrderDraft()，patientId 从 X-Patient-Id header 注入。 */
    private Object dispatchCreateOrderDraft(Map<String, Object> args, String patientId) {
        Long prescriptionId = extractLong(args, "prescription_id");
        Long pharmacyId = extractLong(args, "pharmacy_id");
        String deliveryInfo = extractString(args, "delivery_info");

        if (prescriptionId == null) {
            throw new BusinessException(400, "prescription_id 不能为空");
        }
        if (pharmacyId == null) {
            throw new BusinessException(400, "pharmacy_id 不能为空");
        }

        Long patientIdLong;
        try {
            patientIdLong = Long.parseLong(patientId);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "X-Patient-Id 无效");
        }

        return orderService.createOrderDraft(patientIdLong, prescriptionId, pharmacyId, deliveryInfo);
    }

    // ============ 参数提取辅助 ============

    private static String extractString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        return val instanceof String s ? s : null;
    }

    private static Long extractLong(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
