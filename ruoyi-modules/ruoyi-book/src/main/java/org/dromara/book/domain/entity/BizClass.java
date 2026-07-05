package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 排课对象·班课档案实体（biz_class，PRD-C-213）。班级肖像与学生肖像同构。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_class")
public class BizClass extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 班课名称 */
    private String name;

    /** 年级 1-12（字典 biz_edu_grade；允许 NULL）：当前年级是推导状态（EduTermUtil），不落列 */
    private Integer gradeNo;

    /** gradeNo 生效学年起始年（允许 NULL） */
    private Integer gradeYear;

    /** 教材版本字典码（biz_edu_edition） */
    private String textbookEdition;

    /** 学科字典码（biz_edu_subject） */
    private String subject;

    /** 日历着色 */
    private String color;

    /** 班级肖像 JSON */
    private String profileJson;

    /** 归档：'0' 在册 / '1' 归档 */
    private String archived;
}
