package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 老师全局 AI 记忆响应 VO（PRD-C-100 B4）。
 *
 * <p>含全部展示字段：id/memType/memKey/memValue/source/enabled/confidence/remark/updateTime。
 * list / 新增 / 更新 / toggle 均返本 VO（toggle/更新返单条，list 返 List）。
 *
 * @author PRD-C-100 B4
 */
@Data
public class AiMemoryVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记忆 id */
    private Long id;

    /** 记忆类型（偏好 / 纠正 / 习惯） */
    private String memType;

    /** 记忆键 */
    private String memKey;

    /** 记忆值 */
    private String memValue;

    /** 来源（自动 / 手填） */
    private String source;

    /** 启停（1=启用，0=停用） */
    private Integer enabled;

    /** 置信度 */
    private BigDecimal confidence;

    /** 备注 */
    private String remark;

    /** 最近修改时间 */
    private Date updateTime;
}
