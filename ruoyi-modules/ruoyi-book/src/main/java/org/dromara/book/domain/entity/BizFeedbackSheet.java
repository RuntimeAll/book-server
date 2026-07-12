package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 课后反馈单实体（biz_feedback_sheet，PRD-004 最小版）。
 *
 * <p>一行 = 一张反馈单 = 学生 + 日期 + 标题 + 五列行数组（rows_json）。
 * 五列 = 序号 / 所属模块 / 学习内容 / 掌握情况 / 不足点（实料截图即标准，全自由文本）；
 * 行内可选 kp_id 留画像通路，本卡零功能。
 *
 * <p>🔴 雪花主键 IdType.ASSIGN_ID（表无 AUTO_INCREMENT，MyBatis-Plus 填）。
 * 🔴 表无 tenant_id → Mapper 类级 {@code @InterceptorIgnore(tenantLine="true")}
 * + application.yml tenant.excludes 加 biz_feedback_sheet（双保险，同 biz_export_record）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_feedback_sheet")
public class BizFeedbackSheet extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 学生 id（biz_student.id / biz_teach_target.id）。 */
    private Long targetId;

    /** 反馈单标题（如：乐乐七上暑假数学第一节课上课内容）。 */
    private String title;

    /** 上课日期。 */
    private LocalDate lessonDate;

    /** 行数组 JSON 文本 [{seq,module,content,mastery,weakness,kp_id?}]（五列实料标准）。 */
    private String rowsJson;
}
