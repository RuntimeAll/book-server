package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目三要素长文本外置实体（biz_text_content）— PRD-B-006 收尾增量。
 *
 * <p>把原来直接落在 {@code biz_question.stem_text}(MEDIUMTEXT) 的题干 + 未来要落的答案/解析
 * 文本统一搬到本表，题干 / 答案 / 解析三态对称：
 * <ul>
 *   <li>{@code content_type='S'} — 题干（Stem）</li>
 *   <li>{@code content_type='A'} — 答案（Answer）</li>
 *   <li>{@code content_type='E'} — 解析（Explain）</li>
 * </ul>
 *
 * <p>{@code (question_id, content_type)} UNIQUE — 一题一类型一行。
 *
 * <p>FK 反向引用：{@code biz_question.stem_text_content_id} / {@code answer_text_content_id} /
 * {@code analyze_text_content_id} 分别指向本表 id（FK 由应用层维护，DB 不建外键约束）。
 *
 * <p>过渡期：{@code biz_question.stem_text} 字段保留双写不 DROP，让老查询路径仍可读老字段。
 *
 * @author backend-dev (PRD-B-006)
 */
@Data
@TableName("biz_text_content")
public class BizTextContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 长文本行 ID（AUTO_INCREMENT）
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 反查 biz_question.id
     */
    private Long questionId;

    /**
     * 内容类型 CHAR(1)：'S'=题干 / 'A'=答案 / 'E'=解析
     */
    private String contentType;

    /**
     * 长文本内容（LaTeX 源 / 纯文本，MEDIUMTEXT 列）
     */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
