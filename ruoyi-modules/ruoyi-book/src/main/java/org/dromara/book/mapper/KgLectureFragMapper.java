package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 讲义片段 只读 Mapper（PRD-C-207）。
 *
 * <p>讲义 = 挂 KG 节点（biz_subject）的原子教学片段（表 biz_kg_lecture_frag）。
 * 某层完整讲义 = {@code WHERE subject_id LIKE '<前缀>%' ORDER BY subject_id} 汇聚（前缀零填充=树序，免递归）。
 * 一节点多份 = book_id（教辅套）× owner_id（0官方/个人）。P1 只读官方（owner_id=0）。
 * biz_* 无 tenant_id，关多租户拦截器。
 *
 * @author codeplace-C PRD-C-207
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KgLectureFragMapper {

    /** KG 节点 {id,name,level}（挂载锚，任意层）。 */
    @Select("SELECT id, name, level FROM biz_subject WHERE id = #{subjectId}")
    Map<String, Object> selectNode(@Param("subjectId") String subjectId);

    /**
     * 取某前缀下、某书、官方(owner_id=0)的全部片段，按 subject_id 树序。
     * 返回 {subjectId, kgLevel, contentJson}；contentJson 为片段 Tiptap doc 字符串。
     */
    @Select("SELECT subject_id AS subjectId, kg_level AS kgLevel, content_json AS contentJson " +
        "FROM biz_kg_lecture_frag " +
        "WHERE book_id = #{bookId} AND owner_id = 0 AND subject_id LIKE CONCAT(#{subjectPrefix}, '%') " +
        "ORDER BY subject_id")
    List<Map<String, Object>> selectFragsByPrefix(@Param("bookId") String bookId,
                                                  @Param("subjectPrefix") String subjectPrefix);

    /** 某书片段所在的册前缀（LEFT 3 位=册根 id）。 */
    @Select("SELECT DISTINCT SUBSTRING(subject_id, 1, 3) FROM biz_kg_lecture_frag WHERE book_id = #{bookId} LIMIT 1")
    String selectVolumeOfBook(@Param("bookId") String bookId);

    /**
     * 册内每课时(L4)挂了哪些讲义源（官方）。返回 {lessonId, lessonName, bookId, bookName, owner}。
     * SUBSTRING(,1,12) 把 L4/L5 片段收敛到所属课时；DISTINCT 去重。
     */
    @Select("SELECT DISTINCT SUBSTRING(f.subject_id, 1, 12) AS lessonId, s.name AS lessonName, " +
        "       f.book_id AS bookId, b.series AS bookName, f.owner_id AS owner " +
        "FROM biz_kg_lecture_frag f " +
        "LEFT JOIN biz_subject s ON s.id = SUBSTRING(f.subject_id, 1, 12) " +
        "LEFT JOIN biz_book b ON b.id = f.book_id " +
        "WHERE f.subject_id LIKE CONCAT(#{volumePrefix}, '%') AND f.owner_id = 0 " +
        "ORDER BY lessonId")
    List<Map<String, Object>> selectLessonSources(@Param("volumePrefix") String volumePrefix);
}
