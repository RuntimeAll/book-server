package org.dromara.book.service;

import java.util.Map;

/**
 * 讲义（片段汇聚）Service（PRD-C-207，只读浏览器）。
 *
 * @author codeplace-C PRD-C-207
 */
public interface IKgLectureService {

    /**
     * 取某 KG 节点（任意层）的完整讲义 = 自身 + 子孙片段按树序汇聚成一份 Tiptap doc。
     *
     * @param subjectId 挂载锚 KG 节点 id（课时 L4 常用）
     * @param bookId    教辅套 id，空默认 CC7S
     * @return {node:{id,name,level}, bookId, docJson:object|null}
     */
    Map<String, Object> getLecture(String subjectId, String bookId);

    /**
     * 讲义目录：某书所在册内，每课时挂了哪些讲义源（供左树灰置/来源切换器）。
     *
     * @param bookId 教辅套 id，空默认 CC7S
     * @return {volumeId, lessons:[{lessonId, lessonName, sources:[{bookId, bookName, owner}]}]}
     */
    Map<String, Object> getCatalog(String bookId);
}
