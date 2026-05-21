package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 组卷草稿 VO（/teacher/question/genExamData/ 响应）。
 *
 * <p>misikt 真实行为：用户在试题筐添加完 N 题点击 "组卷" 进工作台时，BE 拉当前用户筐里所有
 * status='1' 已发布题，按题型（questionType）分组生成 sections，返工作台做编辑（本地 state，
 * 草稿不落库 — 跟 misikt 一致）。
 *
 * <p>分组规则（按 misikt 真实行为）：
 * <ul>
 *   <li>questionType=1 → "一、选择题"</li>
 *   <li>questionType=4 → "二、填空题"</li>
 *   <li>questionType=5 → "三、简答题"</li>
 * </ul>
 *
 * <p>title 编号固定按 questionType 1 → 4 → 5 顺序产出（即筐里没有选择题时，第一节直接 "一、填空题"，
 * 不出现的题型不返该 section）。
 *
 * @author backend-dev
 */
@Data
public class ExamDataVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题型分组列表（按 questionType 1→4→5 顺序；空筐返空 list）。
     */
    private List<ExamSectionVo> sections;
}
