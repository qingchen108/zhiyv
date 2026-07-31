package com.smartmed.backend.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.prescription.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 处方 Mapper（06 ticket）。
 */
@Mapper
public interface PrescriptionMapper extends BaseMapper<Prescription> {

    /** 统计某医生的处方引用数（删除医生前置检查，ADR-0006）。 */
    @Select("SELECT COUNT(*) FROM prescription WHERE doctor_id = #{doctorId}")
    long countByDoctorId(Long doctorId);

    /** 统计某问诊的处方数（删除问诊前置检查用）。 */
    @Select("SELECT COUNT(*) FROM prescription WHERE consultation_id = #{consultationId}")
    long countByConsultationId(Long consultationId);
}
