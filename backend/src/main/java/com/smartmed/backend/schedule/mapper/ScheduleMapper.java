package com.smartmed.backend.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 排班 Mapper（04 ticket，MyBatis-Plus BaseMapper + 自定义 SQL）。
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /** 统计某医生的排班记录数（删除医生前置检查，ADR-0006）。 */
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id = #{doctorId}")
    long countByDoctorId(Long doctorId);

    /** 统计某科室的排班记录数（删除科室前置检查，ADR-0006）。 */
    @Select("SELECT COUNT(*) FROM schedule WHERE department_id = #{departmentId}")
    long countByDepartmentId(Long departmentId);

    /** 统计某排班的挂号引用数（删除排班前置检查，ADR-0009）。 */
    @Select("SELECT COUNT(*) FROM registration WHERE schedule_id = #{scheduleId}")
    long countRegistrationByScheduleId(Long scheduleId);
}
