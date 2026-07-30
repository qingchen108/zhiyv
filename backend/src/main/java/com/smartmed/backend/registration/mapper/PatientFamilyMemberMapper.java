package com.smartmed.backend.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.registration.entity.PatientFamilyMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家庭成员 Mapper（05 ticket）。
 */
@Mapper
public interface PatientFamilyMemberMapper extends BaseMapper<PatientFamilyMember> {
}
