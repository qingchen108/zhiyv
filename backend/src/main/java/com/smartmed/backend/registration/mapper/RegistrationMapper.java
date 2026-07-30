package com.smartmed.backend.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.registration.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 挂号 Mapper（05 ticket）。
 */
@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    /** 取序列下一个值（reg_no 生成）。 */
    @Select("SELECT nextval('reg_no_seq')")
    long nextRegNoSeq();

    /** 自动流转：将过期的 REGISTERED 批量标记为 VISITED。 */
    @Update("""
            UPDATE registration r SET status = 'VISITED', updated_at = now()
            FROM schedule s
            WHERE r.schedule_id = s.id
              AND r.status = 'REGISTERED'
              AND (s.schedule_date + s.end_time) < now()
            """)
    int autoMarkVisited();
}
