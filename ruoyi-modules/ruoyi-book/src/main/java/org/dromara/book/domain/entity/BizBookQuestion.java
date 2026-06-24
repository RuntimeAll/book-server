package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 教辅-题目编排关联实体（biz_book_question）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>把一道题挂到某教辅的某容器（章/节/课时）下，记栏目（刷基础/刷提升）、教辅内难度、
 * 角色（母题/变式）、块内顺序。一题可被多个教辅引用（题目对教辅零反向引用，可删不伤核心）。
 *
 * <p>无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("biz_book_question")
public class BizBookQuestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 教辅 id（biz_book.id，NOT NULL） */
    private String bookId;

    /** 题目 id（biz_question.id，NOT NULL） */
    private Long questionId;

    /** 容器类型：section/lesson 等 */
    private String containerType;

    /** 容器 id（biz_book_subject.id） */
    private String containerId;

    /** 栏目类型：刷基础/刷提升等 */
    private String columnType;

    /** 教辅内难度 */
    private String bookDifficulty;

    /** 角色：mother/variant 等 */
    private String role;

    /** 块内顺序 */
    private Integer inBlockSeq;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createTime;
}
