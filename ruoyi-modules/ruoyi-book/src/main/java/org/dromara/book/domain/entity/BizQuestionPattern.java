package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题型（解题 pattern）目录实体（biz_question_pattern）—— PRD-C-204 题型独立可查维度。
 *
 * <p>题型 = 必刷题里「题型1 利用数轴比较大小」这类**解题套路分类**，是独立可查维度，
 * 不进 KG 树（biz_subject）。挂在某知识点/小节（anchor_subject_id = biz_subject.id）下，
 * UNIQUE(anchor_subject_id, name) 幂等。题↔题型多对多走 {@link BizQuestionPatternRel}。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204)
 */
@Data
@TableName("biz_question_pattern")
public class BizQuestionPattern implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 题型名（如「利用数轴比较大小」），NOT NULL，UNIQUE(anchor_subject_id,name) */
    private String name;

    /** 锚知识点/小节（biz_subject.id，varchar(20)），可空，前缀匹配题↔章节同款 id 体系 */
    private String anchorSubjectId;

    /** 来源：'main'=书 / 'AI'，NOT NULL */
    private String source;

    /** 排序，默认 0 */
    private Integer sort;

    /** 描述/解题套路说明 */
    private String description;

    /** 教辅 id（biz_book.id，varchar(8)，可空） */
    private String bookId;

    /** 状态：'0'=启用 / '1'=停用，NOT NULL */
    private String status;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;
}
