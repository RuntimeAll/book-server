package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 排课对象·学生档案实体（biz_student，PRD-C-213）。
 *
 * <p>含学生肖像 profile_json（String 存 JSON，Service 侧 Jackson 解析）。
 * 归属老师 = create_by（BaseEntity 自动填充登录 userId）。
 *
 * @author backend-dev
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_student")
public class BizStudent extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学生姓名 */
    private String name;

    /** 年级 */
    private String grade;

    /** 学科 */
    private String subject;

    /** 教材/进度环境 */
    private String textbook;

    /** 家长电话 */
    private String parentPhone;

    /** 日历着色（空则服务端色板轮转分配） */
    private String color;

    /** 学生肖像 JSON（原样字符串） */
    private String profileJson;

    /** 归档：'0' 在册 / '1' 归档 */
    private String archived;
}
