package org.dromara.ai.config;

import lombok.Data;
import org.dromara.ai.provider.model.AiModelAlias;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 模块配置 — 绑定 application.yml 的 {@code ai:} 段。
 *
 * <p>PRD-B-006 期：仅占位结构（值留空），让 Spring 加载本 bean 即 done；
 * PRD-B-007 期：真正填 providers 凭据 + endpoint + aliases 映射。</p>
 *
 * <p>典型配置示例（B-007 期）：</p>
 * <pre>
 * ai:
 *   enabled: true
 *   default-alias: CLAUDE_FAST
 *   fail-over:
 *     enabled: true
 *     max-attempts: 3
 *   aliases:
 *     CLAUDE_FAST: lk888#claude-3-5-haiku-20241022
 *     CLAUDE_STRONG: lk888#claude-3-5-sonnet-20241022
 *   providers:
 *     lk888:
 *       endpoint: https://api.lk888.xyz
 *       api-key: ${LK888_API_KEY}
 *       timeout: 60s
 * </pre>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 总开关 — B-006 期默认 false（骨架空跑）；B-007 期填 provider 实现后改 true。
     */
    private Boolean enabled = false;

    /**
     * 默认模型别名 — 业务侧 AiRequest 未指定 alias 时使用。
     * B-007 期填 CLAUDE_FAST 等。
     */
    private AiModelAlias defaultAlias;

    /**
     * Fail-over 配置（B-007 实现 AiProviderRouter 时读）
     */
    private FailOver failOver = new FailOver();

    /**
     * 别名映射 — key = 别名字符串（与 {@link AiModelAlias} 名一致），
     * value = "providerName#modelName" 形式，例如 "lk888#claude-3-5-haiku-20241022"。
     * B-006 期空 map；B-007 期填。
     */
    private Map<String, String> aliases = new HashMap<>();

    /**
     * Provider 列表 — key = provider name（如 "lk888"），value = 该 provider 的配置。
     * B-006 期空 map；B-007 期填。
     */
    private Map<String, Provider> providers = new HashMap<>();

    /**
     * Fail-over 子配置
     */
    @Data
    public static class FailOver {
        /** 是否启用 fail-over（B-006 默认 false） */
        private Boolean enabled = false;
        /** 最多尝试次数（含首次） */
        private Integer maxAttempts = 3;
    }

    /**
     * 单 provider 配置 — B-007 期 provider 实现按 name 从本 map 读凭据 / endpoint。
     */
    @Data
    public static class Provider {
        /** API endpoint URL */
        private String endpoint;
        /** API key（生产从环境变量注入，password/ 兜底） */
        private String apiKey;
        /** 请求超时 */
        private Duration timeout = Duration.ofSeconds(60);
    }

}
