package org.dromara.book.service;

import java.util.List;
import java.util.Map;

/**
 * 课件文档 Service（PRD-C-205）。课件 = 一课时一份 Umo/Tiptap 文档 JSON。
 *
 * @author codeplace-C PRD-C-205
 */
public interface IKgDocService {

    /** 读课件文档：{course:{id,name}, bookId, docJson(对象或null)}。 */
    Map<String, Object> getDoc(String courseId, String bookId);

    /** 保存（upsert）课件文档 JSON。返回影响行数。 */
    int saveDoc(String courseId, String bookId, String title, String docJson);

    /** 按 qid 列表批量取题（example 节点渲染用）；qids 为逗号分隔字符串。 */
    List<Map<String, Object>> getQuestions(String qidsCsv);
}
