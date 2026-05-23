package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 试卷题目分组实体（biz_paper_section） — Q 卡 createExamPaper 端点写入用。
 *
 * <p>DDL 字段口径（V1__init_book_tables.sql:188-195）：
 * <ul>
 *   <li>id BIGINT AUTO_INCREMENT</li>
 *   <li>paper_id BIGINT NOT NULL</li>
 *   <li>title VARCHAR(50) NOT NULL — "选择题/填空题/解答题"，Q 卡 V1 固定 "题目"</li>
 *   <li>sort INT NOT NULL — UNIQUE KEY (paper_id, sort)，Q 卡 V1 固定 1</li>
 * </ul>
 *
 * <p>Q 卡跳过项：FE 不展示 section 分组，但 DB 约束 biz_paper_question.section_id NOT NULL 必须有 section。
 * 策略：每张新建卷自动 1 个默认 section（title="题目" / sort=1），所有题挂下面。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_paper_section")
public class BizPaperSection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("paper_id")
    private Long paperId;

    @TableField("title")
    private String title;

    @TableField("sort")
    private Integer sort;
}
