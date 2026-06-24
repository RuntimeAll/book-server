package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 知识点内容块实体（biz_subject_content）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>master 知识点（biz_subject）下挂的内容块：重点/难点/方法/注意/拓展/说明。
 * 一个 subject 下多行，{@code content_type} 为块类型，{@code content} 存文本（含配图 imgUrl 可序进 content）。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("biz_subject_content")
public class BizSubjectContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 知识点 biz_subject.id（varchar(20)，NOT NULL） */
    private String subjectId;

    /** 内容块类型：重点/难点/方法/注意/拓展/说明（NOT NULL） */
    private String contentType;

    /** 内容文本（mediumtext，可含配图标记） */
    private String content;

    /** 来源标记 */
    private String source;

    /** 来源教辅 */
    private String sourceBook;

    /** 排序 */
    private Integer sort;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;
}
