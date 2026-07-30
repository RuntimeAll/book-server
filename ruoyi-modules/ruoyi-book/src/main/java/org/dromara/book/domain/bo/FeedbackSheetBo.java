package org.dromara.book.domain.bo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 反馈单入参 BO（PRD-004）。建/改共用。
 *
 * <p>rows = 五列行数组，每行 {seq,module,content,mastery,weakness,kp_id?}（全自由文本，
 * kp_id 可选）；服务端序列化为 rows_json 存库。id / create_by 服务端登录态取，不信前端。
 *
 * @author backend-dev
 */
@Data
public class FeedbackSheetBo {

    /** 主键（更新态由 path 注入；建单态空）。 */
    private Long id;

    /** 学生 id（雪花，Jackson 由 string 兼容绑定）。 */
    private Long targetId;

    /**
     * 绑定场次（PRD-015 D6 主绑定，雪花）。可空=散单/遗留单。
     * 传了但没传 planId → 服务端从场次冗余回填 planId。
     */
    private Long sessionId;

    /** 冗余计划 id（PRD-015 D6，按计划查/导出用）。可空。 */
    private Long planId;

    /** 反馈批次键（PRD-010 独立批次，不绑课程计划；如"多多五上暑假数学"）。可空=散单。 */
    private String batchKey;

    /**
     * 序号。PRD-010 = 批次内课次号；PRD-015 D7 = <b>计划内反馈序号</b>。
     * 🔴 不传且有 planId（新建）→ 服务端自动 = 该计划下现有反馈 max+1。
     */
    private Integer lessonSeq;

    /** 反馈单标题。 */
    private String title;

    /** 上课日期（yyyy-MM-dd）。 */
    private String lessonDate;

    /** 五列行数组。 */
    private List<Map<String, Object>> rows;
}
