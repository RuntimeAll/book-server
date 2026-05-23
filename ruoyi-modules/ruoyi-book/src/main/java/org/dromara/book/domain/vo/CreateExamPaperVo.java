package org.dromara.book.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Q 卡 段① — 创建试卷出参 VO（POST /teacher/exam/paper/create）。
 *
 * <p>响应：{paperId, questionCount} — FE 拿 paperId 跳 /papers/source/{paperId} 卷详情。
 *
 * @author backend-dev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamPaperVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新创建的试卷 ID（biz_paper.id 自增） */
    private Long paperId;

    /** 试卷内题目数（冗余字段，FE 可立即展示） */
    private Integer questionCount;
}
