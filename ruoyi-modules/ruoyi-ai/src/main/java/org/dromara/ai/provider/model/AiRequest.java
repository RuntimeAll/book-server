package org.dromara.ai.provider.model;

import lombok.Data;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 调用统一入参 — 业务侧无关 provider，只构造此对象交给 {@link org.dromara.ai.provider.IAiTextGenerator}。
 *
 * <p>设计意图：屏蔽 Anthropic / OpenAI / 中转 API 的差异，业务代码不需要为每家 provider 各写一份调用代码。
 * provider 实现层做适配翻译。</p>
 *
 * <p>PRD-B-006 期：字段定型；PRD-B-007 期：业务方按本结构组装请求。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
@Data
public class AiRequest {

    /**
     * 模型别名（推荐使用）— 业务只挑别名，由 {@link org.dromara.ai.config.AiProperties} 的 aliases
     * 映射到具体 provider+model。优先级高于 {@link #model}。
     */
    private AiModelAlias alias;

    /**
     * 具体模型名（绕过别名，直连）— 调试 / 特殊场景使用，正常业务别用。
     * 当 alias 和 model 都设置时，alias 优先。
     */
    private String model;

    /**
     * 对话消息列表 — 至少 1 条（典型一条 user）；多轮对话依次 user / assistant / user / ... 追加。
     * system 提示词可放第一条 role=SYSTEM，由 provider 实现层按各家协议适配。
     */
    private List<Message> messages;

    /**
     * 可用工具列表 — 启用 function calling 时填。null / 空 表示禁用工具。
     */
    private List<Tool> tools;

    /**
     * 工具选择策略 — null/"auto"=LLM 自主决定；"any"=必选一个；"none"=禁用；
     * 也支持指定具体工具名强制调用。各 provider 命名略不同，provider 实现做映射。
     */
    private String toolChoice;

    /**
     * 响应格式约束（JSON / Schema 等）— null 表示自由文本。
     */
    private ResponseFormat responseFormat;

    /**
     * 采样温度 — 0.0~1.0，越高越随机；典型业务用 0.0~0.3（结构化抽取）/ 0.7（创作）。
     * null 走 provider 默认值。
     */
    private Double temperature;

    /**
     * 最大输出 token 数 — null 走 provider 默认；建议显式设值控成本。
     */
    private Integer maxTokens;

    /**
     * 是否流式返回 — true=SSE 流（B-007 暂不实现，留接口），false=一次性返回。
     */
    private Boolean stream;

    /**
     * 业务自定义元数据 — 透传到 {@link org.dromara.ai.observability.AiCallLogEntry} 落日志，
     * 用于关联业务上下文（如 question_id / paper_id / wf_step）。
     * 不会传给 LLM。
     */
    private Map<String, Object> metadata;

    /**
     * 单次调用超时 — null 走 AiProperties 的 provider 级超时（B-007 配置）。
     * 推荐 30~120s 之间，长 prompt + 强模型需更长。
     */
    private Duration timeout;

}
