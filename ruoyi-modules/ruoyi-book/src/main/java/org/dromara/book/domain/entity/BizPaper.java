package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 试卷主表实体（biz_paper） — Q 卡 createExamPaper 端点写入用。
 *
 * <p>DDL 字段口径（V1__init_book_tables.sql:155-182）：
 * <ul>
 *   <li>id BIGINT AUTO_INCREMENT — MP 走 IdType.AUTO 拿回写 ID</li>
 *   <li>create_by VARCHAR(64) — 存数字字符串 String.valueOf(userId)（不是 BIGINT）</li>
 *   <li>status CHAR(1) — 0草稿 1发布 2软删（Q 卡用户拍板创建即 '1' 发布）</li>
 *   <li>paper_type TINYINT — 1手工 2自动（Q 卡固定 1 手工）</li>
 * </ul>
 *
 * <p>现有 BizPaperMapper 6 个自定义 select 方法走 mapper.xml，本 entity 仅供 BaseMapperPlus.insert 使用。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_paper")
public class BizPaper implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("subject_id")
    private String subjectId;

    @TableField("paper_category_id")
    private String paperCategoryId;

    @TableField("directory_name")
    private String directoryName;

    @TableField("question_count")
    private Integer questionCount;

    @TableField("score")
    private BigDecimal score;

    @TableField("suggest_time")
    private Integer suggestTime;

    @TableField("hg_score")
    private BigDecimal hgScore;

    @TableField("paper_type")
    private Integer paperType;

    @TableField("frame_text_content_id")
    private Long frameTextContentId;

    @TableField("exam_year")
    private String examYear;

    @TableField("is_share")
    private Integer isShare;

    @TableField("status")
    private String status;

    @TableField("sort")
    private Integer sort;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableField("remark")
    private String remark;
}
