package com.smartmed.backend.agent.controller;

import com.smartmed.backend.agent.dto.AgentToolRequest;
import com.smartmed.backend.agent.service.AgentToolDispatcher;
import com.smartmed.backend.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 工具泛化路由（09 ticket，ADR-0015，ticket 12 注入 X-Patient-Id）。
 * <p>
 * POST /api/agent/tools/{toolName}，由 Python Agent 侧凭 X-Agent-Secret 调用
 * （AgentSecretFilter 校验，SecurityConfig 对该路径 permitAll）。
 * Body：{ "arguments": {...} }。09 阶段已知工具一律 501（Dispatcher 声明未实现）。
 */
@RestController
@RequestMapping("/api/agent/tools")
@RequiredArgsConstructor
public class AgentToolController {

    private final AgentToolDispatcher dispatcher;
    private final HttpServletRequest request;

    @PostMapping("/{toolName}")
    public Result<Object> invoke(@PathVariable String toolName,
                                 @RequestBody(required = false) AgentToolRequest req) {
        String patientId = request.getHeader("X-Patient-Id");
        return Result.success(dispatcher.dispatch(toolName,
                req == null ? null : req.arguments(), patientId));
    }
}
