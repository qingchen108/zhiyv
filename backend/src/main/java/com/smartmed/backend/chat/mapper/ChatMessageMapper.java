package com.smartmed.backend.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartmed.backend.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 对话消息 Mapper（ticket 10）。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
