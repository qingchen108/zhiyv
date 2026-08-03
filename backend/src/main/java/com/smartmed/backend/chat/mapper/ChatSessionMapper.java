package com.smartmed.backend.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 对话会话 Mapper（ticket 10）。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
