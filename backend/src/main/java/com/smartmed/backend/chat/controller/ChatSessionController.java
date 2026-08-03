package com.smartmed.backend.chat.controller;

import com.smartmed.backend.chat.dto.ChatMessageAppendRequest;
import com.smartmed.backend.chat.dto.ChatMessageVO;
import com.smartmed.backend.chat.dto.ChatSessionCreateRequest;
import com.smartmed.backend.chat.dto.ChatSessionVO;
import com.smartmed.backend.chat.service.ChatService;
import com.smartmed.backend.common.Result;
import com.smartmed.backend.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端对话会话/消息存储接口（ticket 10）。
 * <p>
 * 路径：/api/c/chat/sessions，需 typ=C JWT。
 * 归属校验在 ChatService：非本人会话一律 404（不暴露存在性）。
 */
@RestController
@RequestMapping("/api/c/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatService chatService;

    /** 创建会话（首条消息时调用，title=首条消息截断 ≤20 字）。 */
    @PostMapping
    public Result<ChatSessionVO> create(@Valid @RequestBody ChatSessionCreateRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(chatService.createSession(patientId, req.title()));
    }

    /** 会话列表（updated_at 倒序）。 */
    @GetMapping
    public Result<List<ChatSessionVO>> list() {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(chatService.listSessions(patientId));
    }

    /** 删除会话（物理删除 + 级联删消息）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        chatService.deleteSession(patientId, id);
        return Result.success();
    }

    /** 会话消息列表（created_at 升序）。 */
    @GetMapping("/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        Long patientId = SecurityUtil.current().getPatientId();
        return Result.success(chatService.listMessages(patientId, id));
    }

    /** 批量追加消息（每轮一次原子保存：user + tool×N + assistant）。 */
    @PostMapping("/{id}/messages")
    public Result<Void> append(@PathVariable Long id, @Valid @RequestBody ChatMessageAppendRequest req) {
        Long patientId = SecurityUtil.current().getPatientId();
        chatService.appendMessages(patientId, id, req);
        return Result.success();
    }
}
