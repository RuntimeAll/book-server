package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 课件文档 只读/读写 Mapper（PRD-C-205）。
 *
 * <p>课件 = 一课时一份 Umo/Tiptap 文档 JSON（表 biz_kg_doc）。example 自定义节点只存 qid，
 * 展示/导出时按 qid 批量取题（biz_question + biz_text_content）。BIGINT 一律 CAST CHAR 防精度丢失，
 * 关多租户拦截器（biz_* 无 tenant_id）。
 *
 * @author codeplace-C PRD-C-205
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KgDocMapper {

    /** 课时节点 {id,name}（level4）。 */
    @Select("SELECT id, name FROM biz_subject WHERE id = #{courseId} AND level = 4")
    Map<String, Object> selectCourse(@Param("courseId") String courseId);

    /** 读某课时某书的课件文档 JSON（取最新一条）。 */
    @Select("SELECT doc_json FROM biz_kg_doc WHERE course_id = #{courseId} AND book_id = #{bookId} " +
        "ORDER BY update_time DESC LIMIT 1")
    String selectDocJson(@Param("courseId") String courseId, @Param("bookId") String bookId);

    /** upsert 课件文档 JSON（course_id+book_id 唯一）。 */
    @Update("INSERT INTO biz_kg_doc(course_id, book_id, title, doc_json, status, source, source_book) " +
        "VALUES(#{courseId}, #{bookId}, #{title}, #{docJson}, '0', '书', #{bookId}) " +
        "ON DUPLICATE KEY UPDATE title = VALUES(title), doc_json = VALUES(doc_json), update_time = NOW()")
    int upsertDoc(@Param("courseId") String courseId, @Param("bookId") String bookId,
                  @Param("title") String title, @Param("docJson") String docJson);

    /**
     * 按 qid 批量取题面/答案/详解（example 节点渲染/导出用）。
     * qids 为已校验的数字字符串列表（controller 层过滤非数字，防注入）。
     */
    @Select("<script>" +
        "SELECT CAST(q.id AS CHAR) AS qid, q.question_type AS type, q.source_raw AS source, " +
        "       MAX(CASE WHEN tc.content_type = 'S' THEN tc.content END) AS stem, " +
        "       MAX(CASE WHEN tc.content_type = 'A' THEN tc.content END) AS answer, " +
        "       MAX(CASE WHEN tc.content_type = 'E' THEN tc.content END) AS explainText " +
        "FROM biz_question q LEFT JOIN biz_text_content tc ON tc.question_id = q.id " +
        "WHERE q.id IN " +
        "<foreach item='id' collection='qids' open='(' separator=',' close=')'>#{id}</foreach> " +
        "GROUP BY q.id, q.question_type, q.source_raw" +
        "</script>")
    List<Map<String, Object>> selectQuestionsByIds(@Param("qids") List<Long> qids);
}
