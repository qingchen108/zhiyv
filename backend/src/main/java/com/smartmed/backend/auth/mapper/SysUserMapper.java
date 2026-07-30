package com.smartmed.backend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** sys_user Mapper。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
