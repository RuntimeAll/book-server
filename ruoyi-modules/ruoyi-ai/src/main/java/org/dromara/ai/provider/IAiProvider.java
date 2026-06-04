package org.dromara.ai.provider;

import org.dromara.ai.provider.model.AiCapability;
import org.dromara.ai.provider.model.AiHealthStatus;

import java.util.Set;

/**
 * AI provider 顶层契约 — 所有 LLM provider 实现（Lk888 / Aigeekapi / Anthropic 直连 等）必实现。
 *
 * <p>本接口定义 provider 的"元信息" + "健康查询"能力。具体生成能力按职责拆分到
 * {@link IAiTextGenerator}（文本）/ {@link IAiVisionGenerator}（视觉）子接口，
 * provider 实现类可按需选择实现哪些子接口。</p>
 *
 * <p>PRD-B-006 期：仅定义契约；PRD-B-007 期：实现类填实现。</p>
 *
 * @author backend-dev
 * @since 2026-06-04 (PRD-B-006)
 */
public interface IAiProvider {

    /**
     * Provider 唯一标识 — 小写字母数字横线，例如 "lk888" / "aigeekapi" / "anthropic-direct"。
     * router 选路时按此 name 在 {@link org.dromara.ai.config.AiProperties#getProviders()} 找配置。
     *
     * @return provider 名（非空）
     */
    String name();

    /**
     * Provider 能力声明 — router 按能力过滤候选 provider（如要 VISION 只挑实现了视觉的）。
     *
     * @return 本 provider 支持的能力集合（非空，至少含 {@link AiCapability#TEXT}）
     */
    Set<AiCapability> capabilities();

    /**
     * 健康状态查询 — router 在派单前检本状态，DOWN 跳过 / DEGRADED 降优先级。
     *
     * <p>实现要求：本方法应是 O(1) 内存读（从熔断器 / 健康探测器读缓存），
     * 不可发起远程调用阻塞。</p>
     *
     * @return 当前健康状态
     */
    AiHealthStatus health();

}
