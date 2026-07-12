package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 书架·书实体（biz_shelf_book，PRD-002 D1）。讲义/练习册/专项统一实体，不同 book_type 编排风格不同。
 *
 * <p>JSON 列（style_meta_json）以 String 存取，业务层用 JsonUtils 序列化（对齐 biz_course_plan_lesson.paper_slots）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_shelf_book")
public class BizShelfBook extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** lecture 讲义型 / workbook 练习册型 / special 专项 */
    private String bookType;

    /** 书名 */
    private String title;

    /** 学科（对齐 biz_ingest_job.subject_id 口径） */
    private String subjectId;

    /** 年级 */
    private String grade;

    /** 教材版本 */
    private String edition;

    /** 归属老师 user_id */
    private Long ownerId;

    /** 0 正常 / 1 归档 */
    private String status;

    /** 编排风格元数据 JSON（导出主题等） */
    private String styleMetaJson;

    /** 录入直出书溯源：来源 biz_ingest_job.id（PRD-001 D6） */
    private Long sourceJobId;

    private String remark;
}
