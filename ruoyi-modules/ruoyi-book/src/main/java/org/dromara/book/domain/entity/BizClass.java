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

    /** 年级 */
    private String grade;

    /** 学科 */
    private String subject;

    /** 日历着色 */
    private String color;

    /** 班级肖像 JSON */
    private String profileJson;

    /** 归档：'0' 在册 / '1' 归档 */
    private String archived;
}
