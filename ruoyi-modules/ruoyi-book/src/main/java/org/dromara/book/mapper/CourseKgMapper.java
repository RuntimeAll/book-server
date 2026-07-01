package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 课时知识梳理讲义 只读 Mapper（GET /teacher/kg/course/{courseId}）。
 *
 * <p>纯查询、不继承 BaseMapper（无实体）。BIGINT question_id 一律 CAST 成 CHAR 返回，
 * 防前端/JSON JS 数字精度丢失。所有表无 tenant_id，关多租户拦截器。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface CourseKgMapper {

    /** 课时节点 {id,name}（level4）。 */
    @Select("SELECT id, name FROM biz_subject WHERE id = #{courseId} AND level = 4")
    Map<String, Object> selectCourse(@Param("courseId") String courseId);

    /** 课时节点上 content_type=思维导图 的原始 content（含 <img src>）；取 sort 最小一条。 */
    @Select("SELECT content FROM biz_subject_content WHERE subject_id = #{courseId} " +
        "AND content_type = '思维导图' ORDER BY sort LIMIT 1")
    String selectMindmapContent(@Param("courseId") String courseId);

    /** 考点（level5，直接子节点），按 sort。 */
    @Select("SELECT id, name FROM biz_subject WHERE parent_id = #{courseId} AND level = 5 ORDER BY sort")
    List<Map<String, Object>> selectKaodians(@Param("courseId") String courseId);

    /** 某考点下的知识点（level6），按 sort。 */
    @Select("SELECT id, name FROM biz_subject WHERE parent_id = #{kaodianId} AND level = 6 ORDER BY sort")
    List<Map<String, Object>> selectKps(@Param("kaodianId") String kaodianId);

    /** 某知识点正文块（讲解/名师解读/表格），按 sort。 */
    @Select("SELECT content_type AS type, content FROM biz_subject_content " +
        "WHERE subject_id = #{kpId} AND content_type IN ('讲解','名师解读','表格') ORDER BY sort")
    List<Map<String, Object>> selectBlocks(@Param("kpId") String kpId);

    /** 某知识点标红记忆点关键字，按 sort。 */
    @Select("SELECT concept FROM biz_key_concept WHERE subject_id = #{kpId} ORDER BY sort")
    List<String> selectKeyConcepts(@Param("kpId") String kpId);

    /**
     * 课时下某栏目（column_type）的题目 + 主知识点 + S/A/E 文本，平铺按 in_block_seq。
     * qid CAST 成 CHAR 防精度丢失；主知识点取 is_primary=1 的（可空）。
     */
    @Select("SELECT CAST(bq.question_id AS CHAR) AS qid, " +
        "       bq.in_block_seq AS inBlockSeq, " +
        "       q.question_type AS type, " +
        "       q.source_raw AS source, " +
        "       (SELECT CAST(qk.knowledge_id AS CHAR) FROM biz_question_knowledge qk " +
        "          WHERE qk.question_id = bq.question_id AND qk.is_primary = 1 LIMIT 1) AS primaryKnowledgeId, " +
        "       MAX(CASE WHEN tc.content_type = 'S' THEN tc.content END) AS stem, " +
        "       MAX(CASE WHEN tc.content_type = 'A' THEN tc.content END) AS answer, " +
        "       MAX(CASE WHEN tc.content_type = 'E' THEN tc.content END) AS explainText " +
        "FROM biz_book_question bq " +
        "JOIN biz_question q ON q.id = bq.question_id " +
        "LEFT JOIN biz_text_content tc ON tc.question_id = bq.question_id " +
        "WHERE bq.book_id = #{bookId} AND bq.container_id = #{courseId} AND bq.column_type = #{columnType} " +
        "GROUP BY bq.question_id, bq.in_block_seq, q.question_type, q.source_raw " +
        "ORDER BY bq.in_block_seq")
    List<Map<String, Object>> selectQuestionsByColumn(@Param("bookId") String bookId,
                                                       @Param("courseId") String courseId,
                                                       @Param("columnType") String columnType);
}
