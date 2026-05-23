package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 试卷-题目关联实体（biz_paper_question） — Q 卡 createExamPaper 端点批量写入用。
 *
 * <p>DDL 字段口径（V1__init_book_tables.sql:201-212）：
 * <ul>
 *   <li>id BIGINT AUTO_INCREMENT</li>
 *   <li>paper_id BIGINT NOT NULL</li>
 *   <li>section_id BIGINT NOT NULL ⚠️ 必须挂在某个 section 下，不能裸挂 paper</li>
 *   <li>question_id BIGINT NOT NULL</li>
 *   <li>sort INT NOT NULL — UNIQUE KEY (section_id, sort)，Q 卡按试题栏 LS 顺序 1/2/3...</li>
 *   <li>score DECIMAL(5,2) DEFAULT 0 — Q 卡 V1 固定 0（不录入分值）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@TableName("biz_paper_question")
public class BizPaperQuestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private Long paperId;

    @TableField("section_id")
    private Long sectionId;

    @TableField("question_id")
    private Long questionId;

    @TableField("sort")
    private Integer sort;

    @TableField("score")
    private BigDecimal score;
}
