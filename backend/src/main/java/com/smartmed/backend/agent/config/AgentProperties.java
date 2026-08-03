package com.smartmed.backend.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 配置（09 ticket，smartmed.agent.*）。
 * <p>
 * secret 与 Python 侧 AGENT_SECRET 同值（双向鉴权，见 CONTEXT §5）；
 * base-url 指向 Python Agent 服务；first-token-timeout-ms 为网关首 token 等待上限。
 */
@Data
@Component
@ConfigurationProperties(prefix = "smartmed.agent")
public class AgentProperties {

    /** 双向鉴权共享密钥（X-Agent-Secret header）。 */
    private String secret;

    /** Python Agent 服务基址（如 http://localhost:8000）。 */
    private String baseUrl;

    /** 首 token 等待上限（ms），超时视为 Agent 服务失败（契约：HTTP 502）。 */
    private long firstTokenTimeoutMs = 60_000;
}
