package com.smartmed.backend.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartmed.backend.chat.dto.ChatMessageAppendRequest;
import com.smartmed.backend.chat.dto.ChatMessageVO;
import com.smartmed.backend.chat.dto.ChatSessionVO;
import com.smartmed.backend.chat.entity.ChatMessage;
import com.smartmed.backend.chat.entity.ChatSession;
import com.smartmed.backend.chat.mapper.ChatMessageMapper;
import com.smartmed.backend.chat.mapper.ChatSessionMapper;
import com.smartmed.backend.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 对话会话/消息存储（ticket 10，CONTEXT §8）。
 * <p>
 * 存储责任在前端：done 后每轮批量原子追加；Java 网关零业务解析。
 * 归属校验：session.patient_id == 操作人，非本人一律 404（不暴露会话存在性）。
 * 删除：物理删除 + 级联删消息（CONTEXT §2 物理删除策略）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    /** 创建会话（首条消息时前端调用，title=首条消息截断 ≤20 字）。 */
    @Transactional
    public ChatSessionVO createSession(Long patientId, String title) {
        ChatSession session = new ChatSession();
        session.setPatientId(patientId);
        session.setTitle(title);
        sessionMapper.insert(session);
        return new ChatSessionVO(session.getId(), session.getTitle(), session.getUpdatedAt());
    }

    /** 会话列表（updated_at 倒序）。 */
    public List<ChatSessionVO> listSessions(Long patientId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getPatientId, patientId)
                        .orderByDesc(ChatSession::getUpdatedAt))
                .stream()
                .map(s -> new ChatSessionVO(s.getId(), s.getTitle(), s.getUpdatedAt()))
                .toList();
    }

    /** 会话消息列表（created_at 升序，历史渲染顺序）。 */
    public List<ChatMessageVO> listMessages(Long patientId, Long sessionId) {
        ChatSession session = getOwnedSession(patientId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByAsc(ChatMessage::getCreatedAt))
                .stream()
                .map(m -> new ChatMessageVO(m.getId(), m.getRole(), m.getContent(), m.getToolTrace(), m.getCreatedAt()))
                .toList();
    }

    /**
     * 批量追加消息（每轮一次原子保存）。
     * <p>
     * 同一事务落库全部消息并刷新 session.updated_at（会话列表排序依据）。
     * 失败轮次由前端不调用本接口兜底（不落库）。
     */
    @Transactional
    public void appendMessages(Long patientId, Long sessionId, ChatMessageAppendRequest req) {
        ChatSession session = getOwnedSession(patientId, sessionId);
        for (ChatMessageAppendRequest.Message m : req.messages()) {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(session.getId());
            msg.setRole(m.role());
            msg.setContent(m.content());
            msg.setToolTrace(m.toolTrace());
            messageMapper.insert(msg);
        }
        // 刷新会话更新时间（列表 updated_at 倒序）
        sessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getId, session.getId())
                .setSql("updated_at = now()"));
    }

    /** 删除会话（物理删除 + 级联删消息）。 */
    @Transactional
    public void deleteSession(Long patientId, Long sessionId) {
        ChatSession session = getOwnedSession(patientId, sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId()));
        sessionMapper.deleteById(session.getId());
    }

    /** 归属校验：取本人会话，非本人/不存在一律 404。 */
    private ChatSession getOwnedSession(Long patientId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !patientId.equals(session.getPatientId())) {
            throw new BusinessException(404, "会话不存在");
        }
        return session;
    }
}
