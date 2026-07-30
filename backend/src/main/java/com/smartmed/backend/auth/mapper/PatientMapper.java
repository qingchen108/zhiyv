package com.smartmed.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.auth.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

/** patient Mapper。 */
@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}
