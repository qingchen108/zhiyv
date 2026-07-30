package com.smartmed.backend.doctor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 排班表只读 Mapper（03 仅用于医生/科室删除前置检查，ADR-0006）。
 * <p>
 * 不建完整实体/Service，排班 CRUD 留待 04 ticket。
 */
@Mapper
public interface ScheduleMapper {

    /** 统计某医生的排班记录数（>0 则禁止删除该医生）。 */
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id = #{doctorId}")
    long countByDoctorId(Long doctorId);

    /** 统计某科室的排班记录数（>0 则禁止删除该科室）。 */
    @Select("SELECT COUNT(*) FROM schedule WHERE department_id = #{departmentId}")
    long countByDepartmentId(Long departmentId);
}
