package org.dromara.book.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.math.BigDecimal;

/**
 * /teacher/exam/paper/page 响应 list 元素 VO（D 卡卷库视觉级还原）。
 *
 * <p>字段命名严格对齐 misikt 真响应（A6-paper-page.json）字节级口径：
 * <ul>
 *   <li>{@code id} BIGINT — biz_paper.id</li>
 *   <li>{@code score} BigDecimal — 保留卷内小数总分</li>
 *   <li>{@code createTime} STRING 'YYYY-MM-DD' — misikt 真响应是日期串，跟 select 接口 ms timestamp 区别</li>
 *   <li>{@code status} Integer — misikt 真响应 1 整数，不是 '1' 字符串（DB CHAR(1) 转 Integer）</li>
 *   <li>{@code paperType} Integer — misikt 真响应 1 / 2 整数</li>
 *   <li>{@code createUser} Long — misikt 真响应小整数（D 卡 ETL admin id）；Q 卡后 RuoYi 雪花 19 位 user_id 需 Long 容纳（详 Q 卡 hotfix 沉淀）</li>
 *   <li>{@code finishTime} 恒 null（DB 无此列 — misikt 真响应也都 null）</li>
 *   <li>{@code sort} Integer — DB INT，通常 = id</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：删除 biz_paper 3 字段（详见 PRD-B-013 §scope.A）。
 *
 * @author backend-dev
 */
@Data
@JsonPropertyOrder({
    "id", "name", "questionCount", "score", "suggestTime",
    "createTime", "finishTime", "createUser",
    "subjectId", "paperCategoryId", "paperType", "status", "sort",
    "published", "canChangeVisibility", "canManage"
})
public class PaperListItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 试卷 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 试卷名 */
    private String name;

    /** 题目数（冗余字段，from biz_paper.question_count） */
    private Integer questionCount;

    /** 卷总分，保留DECIMAL(8,2)小数精度。 */
    private BigDecimal score;

    /** 建议时长（分钟） */
    private Integer suggestTime;

    /** 创建时间，misikt 真响应 STRING 'YYYY-MM-DD'；Jackson 按 @JsonFormat 序列化 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createTime;

    /** 完成时间，DB 无此字段，恒 null */
    private String finishTime;

    /**
     * 创建人 id，DB biz_paper.create_by VARCHAR(64) → Long。
     * D 卡 ETL admin id 是小整数（misikt 真响应口径 Integer），Q 卡 RuoYi 注册 user_id 是雪花 19 位（超 Integer.MAX）。
     * Serialize every ID as a string, including small administrator IDs, to preserve identity exactly.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUser;

    /** Current caller may edit and delete this paper. */
    private boolean canManage;

    /** status='1'；由服务层从状态字段派生。 */
    private boolean published;

    /** 当前调用者是否可切换该卷公开状态。 */
    private boolean canChangeVisibility;

    /** 仅供服务层判断官方普通卷，不对外输出。 */
    @JsonIgnore
    private String paperKind;

    /** 卷分类 id（biz_paper.subject_id） */
    private String subjectId;

    private String paperCategoryId;

    /** 卷类型 1=日常 2=月考 6=专题 */
    private Integer paperType;

    /** 状态：misikt 真响应 1 整数（DB CHAR(1) '0'/'1'/'2' → Integer 对齐） */
    private Integer status;

    /** 排序键（通常 = id） */
    private Integer sort;
}
