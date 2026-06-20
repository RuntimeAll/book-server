package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 老师全局 AI 记忆 新增/更新 入参 BO（PRD-C-100 B4）。
 *
 * <p>新增（POST /teacher/ai-memory）+ 更新（PUT /teacher/ai-memory/{id}）共用：
 * <ul>
 *   <li>{@code memType} / {@code memKey} / {@code memValue} 必填</li>
 *   <li>{@code source} 缺省「手填」（Python 自动写传「自动」）</li>
 *   <li>{@code confidence} / {@code remark} 可选</li>
 * </ul>
 *
 * <p>🔴 绝不信任前端传的归属字段，teacher_id 永远以登录态 LoginHelper.getUserId() 为准。
 *
 * @author PRD-C-100 B4
 */
@Data
public class AiMemoryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记忆类型（偏好 / 纠正 / 习惯） */
    private String memType;

    /** 记忆键（如「常教年级」「教材版本」） */
    private String memKey;

    /** 记忆值 */
    private String memValue;

    /** 来源（自动 / 手填），缺省「手填」 */
    private String source;

    /** 置信度（可选） */
    private BigDecimal confidence;

    /** 备注（可选） */
    private String remark;
}
