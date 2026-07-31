package com.smartmed.backend.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.prescription.entity.PrescriptionTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 处方模板 Mapper（06 ticket）。
 */
@Mapper
public interface PrescriptionTemplateMapper extends BaseMapper<PrescriptionTemplate> {

    /** 统计某医生的处方模板引用数（删除医生前置检查，ADR-0006）。 */
    @Select("SELECT COUNT(*) FROM prescription_template WHERE doctor_id = #{doctorId}")
    long countByDoctorId(Long doctorId);
}
