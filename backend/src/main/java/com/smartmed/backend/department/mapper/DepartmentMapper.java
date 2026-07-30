package com.smartmed.backend.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.department.entity.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
