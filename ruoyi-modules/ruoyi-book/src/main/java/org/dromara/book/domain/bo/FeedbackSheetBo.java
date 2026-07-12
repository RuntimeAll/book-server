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

    /** 反馈单标题。 */
    private String title;

    /** 上课日期（yyyy-MM-dd）。 */
    private String lessonDate;

    /** 五列行数组。 */
    private List<Map<String, Object>> rows;
}
