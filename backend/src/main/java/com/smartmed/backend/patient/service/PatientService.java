package com.smartmed.backend.patient.service;

import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.patient.dto.FamilyMemberRequest;
import com.smartmed.backend.patient.dto.FamilyMemberVO;
import com.smartmed.backend.patient.dto.PatientProfileUpdateRequest;
import com.smartmed.backend.patient.dto.PatientProfileVO;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import com.smartmed.backend.registration.mapper.PatientFamilyMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Objects;

/**
 * C 端患者档案与家庭成员服务（07 ticket）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;
    private final PatientFamilyMemberMapper familyMemberMapper;

    // ==================== 患者档案 ====================

    /** 获取当前患者档案（含派生 age + 脱敏 phone）。 */
    public PatientProfileVO getProfile(Long patientId) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw new BusinessException(404, "患者档案不存在");
        }
        return toProfileVO(patient);
    }

    /** 更新当前患者档案（仅更新传入的非空字段）。 */
    @Transactional
    public PatientProfileVO updateProfile(Long patientId, PatientProfileUpdateRequest req) {
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            throw new BusinessException(404, "患者档案不存在");
        }

        if (req.getName() != null) {
            patient.setName(req.getName());
        }
        if (req.getGender() != null) {
            patient.setGender(req.getGender());
        }
        if (req.getBirthDate() != null) {
            patient.setBirthDate(req.getBirthDate());
        }
        if (req.getAllergyHistory() != null) {
            patient.setAllergyHistory(req.getAllergyHistory());
        }

        patientMapper.updateById(patient);
        log.info("患者档案更新: patientId={}", patientId);
        return toProfileVO(patient);
    }

    // ==================== 家庭成员 ====================

    /** 获取当前患者的家庭成员列表。 */
    public List<FamilyMemberVO> listFamilyMembers(Long patientId) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PatientFamilyMember>()
                .eq(PatientFamilyMember::getPatientId, patientId)
                .orderByAsc(PatientFamilyMember::getId);
        List<PatientFamilyMember> list = familyMemberMapper.selectList(wrapper);
        return list.stream().map(this::toFamilyMemberVO).toList();
    }

    /** 新增家庭成员。 */
    @Transactional
    public FamilyMemberVO addFamilyMember(Long patientId, FamilyMemberRequest req) {
        PatientFamilyMember fm = new PatientFamilyMember();
        fm.setPatientId(patientId);
        fm.setName(req.getName());
        fm.setRelationship(req.getRelationship());
        fm.setPhone(req.getPhone());
        fm.setGender(req.getGender());
        fm.setBirthDate(req.getBirthDate());
        fm.setAllergyHistory(req.getAllergyHistory());
        familyMemberMapper.insert(fm);
        log.info("新增家庭成员: patientId={}, familyMemberId={}", patientId, fm.getId());
        return toFamilyMemberVO(fm);
    }

    /** 更新家庭成员。 */
    @Transactional
    public FamilyMemberVO updateFamilyMember(Long patientId, Long memberId, FamilyMemberRequest req) {
        PatientFamilyMember fm = familyMemberMapper.selectById(memberId);
        if (fm == null) {
            throw new BusinessException(404, "家庭成员不存在");
        }
        if (!Objects.equals(fm.getPatientId(), patientId)) {
            throw new BusinessException(403, "无权操作此家庭成员");
        }

        fm.setName(req.getName());
        fm.setRelationship(req.getRelationship());
        if (req.getPhone() != null) {
            fm.setPhone(req.getPhone());
        }
        if (req.getGender() != null) {
            fm.setGender(req.getGender());
        }
        if (req.getBirthDate() != null) {
            fm.setBirthDate(req.getBirthDate());
        }
        if (req.getAllergyHistory() != null) {
            fm.setAllergyHistory(req.getAllergyHistory());
        }
        familyMemberMapper.updateById(fm);
        log.info("更新家庭成员: familyMemberId={}", memberId);
        return toFamilyMemberVO(fm);
    }

    /** 删除家庭成员。 */
    @Transactional
    public void deleteFamilyMember(Long patientId, Long memberId) {
        PatientFamilyMember fm = familyMemberMapper.selectById(memberId);
        if (fm == null) {
            throw new BusinessException(404, "家庭成员不存在");
        }
        if (!Objects.equals(fm.getPatientId(), patientId)) {
            throw new BusinessException(403, "无权操作此家庭成员");
        }
        // 物理删除（ADR-0006：无前置引用检查，家庭成员删除不涉及其他实体引用）
        familyMemberMapper.deleteById(memberId);
        log.info("删除家庭成员: familyMemberId={}", memberId);
    }

    // ==================== 内部方法 ====================

    /** 计算年龄（由 birthDate 派生，与 DoctorVO 口径一致）。 */
    private Integer calcAge(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /** 手机号脱敏：中间四位变 ****。 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private PatientProfileVO toProfileVO(Patient patient) {
        return PatientProfileVO.builder()
                .id(patient.getId())
                .name(patient.getName())
                .phone(maskPhone(patient.getPhone()))
                .gender(patient.getGender())
                .birthDate(patient.getBirthDate())
                .age(calcAge(patient.getBirthDate()))
                .allergyHistory(patient.getAllergyHistory())
                .build();
    }

    private FamilyMemberVO toFamilyMemberVO(PatientFamilyMember fm) {
        return FamilyMemberVO.builder()
                .id(fm.getId())
                .name(fm.getName())
                .relationship(fm.getRelationship())
                .phone(maskPhone(fm.getPhone()))
                .gender(fm.getGender())
                .birthDate(fm.getBirthDate())
                .age(calcAge(fm.getBirthDate()))
                .allergyHistory(fm.getAllergyHistory())
                .createdAt(fm.getCreatedAt())
                .build();
    }
}