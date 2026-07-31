package com.smartmed.backend.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.consultation.entity.Consultation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 问诊 Mapper（06 ticket）。
 */
@Mapper
public interface ConsultationMapper extends BaseMapper<Consultation> {

    /** 统计某医生的问诊引用数（删除医生前置检查，ADR-0006）。 */
    @Select("SELECT COUNT(*) FROM consultation WHERE doctor_id = #{doctorId}")
    long countByDoctorId(Long doctorId);

    /** 将某挂号关联的问诊状态批量标记为 COMPLETED（删除挂号前置检查用，ADR-0006）。 */
    @Update("UPDATE consultation SET status='COMPLETED', updated_at=now() WHERE registration_id = #{registrationId}")
    int markCompletedByRegistration(Long registrationId);

    /**
     * 今日待接诊列表：该医生今日排班的 WAITING 问诊，按班次顺序 + reg_no 升序。
     * 班次排序靠 CASE 映射为数字（MORNING=1/AFTERNOON=2/EVENING=3）。
     */
    @Select("""
            SELECT c.* FROM consultation c
            JOIN registration r ON c.registration_id = r.id
            JOIN schedule s ON r.schedule_id = s.id
            WHERE c.doctor_id = #{doctorId}
              AND c.status = 'WAITING'
              AND s.schedule_date = #{today}
            ORDER BY CASE s.time_period
                       WHEN 'MORNING' THEN 1
                       WHEN 'AFTERNOON' THEN 2
                       WHEN 'EVENING' THEN 3
                       ELSE 9 END,
                     r.reg_no ASC
            """)
    List<Consultation> findTodayWaiting(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);

    /** 按实际就诊人查历史挂号（ADR-0010 口径）：family_member_id 非空按成员查。 */
    @Select("""
            SELECT r.* FROM registration r
            WHERE r.family_member_id = #{familyMemberId}
            ORDER BY r.created_at DESC
            """)
    List<com.smartmed.backend.registration.entity.Registration> findRegistrationsByFamilyMember(Long familyMemberId);

    /** 按实际就诊人查历史挂号（ADR-0010 口径）：本人就诊（family_member_id IS NULL）。 */
    @Select("""
            SELECT r.* FROM registration r
            WHERE r.patient_id = #{patientId} AND r.family_member_id IS NULL
            ORDER BY r.created_at DESC
            """)
    List<com.smartmed.backend.registration.entity.Registration> findRegistrationsBySelf(Long patientId);
}
