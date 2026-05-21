package org.dromara.book.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * /teacher/exam/paper/page 响应 list 元素 VO（D 卡卷库视觉级还原）。
 *
 * <p>字段命名严格对齐 misikt 真响应（A6-paper-page.json）字节级口径：
 * <ul>
 *   <li>{@code id} BIGINT — biz_paper.id</li>
 *   <li>{@code score} Integer — misikt 真响应是整数 33 / 120，不是 33.00；DB DECIMAL(6,2) 转 Integer 去小数</li>
 *   <li>{@code createTime} STRING 'YYYY-MM-DD' — misikt 真响应是日期串，跟 select 接口 ms timestamp 区别</li>
 *   <li>{@code status} Integer — misikt 真响应 1 整数，不是 '1' 字符串（DB CHAR(1) 转 Integer）</li>
 *   <li>{@code paperType} Integer — misikt 真响应 1 / 2 整数</li>
 *   <li>{@code createUser} Integer — misikt 真响应 2 整数（DB create_by VARCHAR(64) 转 Integer）</li>
 *   <li>{@code finishTime} 恒 null（DB 无此列 — misikt 真响应也都 null）</li>
 *   <li>{@code hgScore} / {@code directoryName} / {@code frameTextContentId} 大多 null（透传 DB 原值）</li>
 *   <li>{@code sort} Integer — DB INT，通常 = id</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@JsonPropertyOrder({
    "id", "name", "questionCount", "score", "suggestTime",
    "createTime", "finishTime", "hgScore", "createUser", "directoryName",
    "subjectId", "paperType", "frameTextContentId", "status", "sort"
})
public class PaperListItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 试卷 id */
    private Long id;

    /** 试卷名 */
    private String name;

    /** 题目数（冗余字段，from biz_paper.question_count） */
    private Integer questionCount;

    /** 卷总分（冗余字段，from biz_paper.score；DB DECIMAL(6,2) → Integer 去小数对齐 misikt） */
    private Integer score;

    /** 建议时长（分钟） */
    private Integer suggestTime;

    /** 创建时间，misikt 真响应 STRING 'YYYY-MM-DD'；Jackson 按 @JsonFormat 序列化 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date createTime;

    /** 完成时间，DB 无此字段，恒 null */
    private String finishTime;

    /** 合格分（DB DECIMAL，misikt 真响应一般 null） */
    private Integer hgScore;

    /** 创建人 id，DB biz_paper.create_by VARCHAR → Integer 对齐 misikt */
    private Integer createUser;

    /** 目录名（misikt 真响应一般 null） */
    private String directoryName;

    /** 卷分类 id（biz_paper.subject_id） */
    private String subjectId;

    /** 卷类型 1=日常 2=月考 6=专题 */
    private Integer paperType;

    /** 资源帧文本内容 id（misikt 业务字段，一般 null） */
    private Long frameTextContentId;

    /** 状态：misikt 真响应 1 整数（DB CHAR(1) '0'/'1'/'2' → Integer 对齐） */
    private Integer status;

    /** 排序键（通常 = id） */
    private Integer sort;
}
