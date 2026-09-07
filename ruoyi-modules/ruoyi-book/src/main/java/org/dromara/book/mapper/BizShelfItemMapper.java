package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.vo.ShelfReadVo;

import java.util.List;

/**
 * 书架·内容项 Mapper（biz_shelf_item，PRD-002）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂（biz_* 表无 tenant_id，注解不随父接口继承）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizShelfItemMapper extends BizBaseMapper<BizShelfItem> {
    @Select("""
        <script>
        SELECT book_id AS id, COUNT(*) AS item_count,
               SUM(CASE WHEN kind = 'question' THEN 1 ELSE 0 END) AS question_count
        FROM biz_shelf_item WHERE book_id IN
        <foreach collection="bookIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY book_id
        </script>
        """)
    List<ShelfReadVo.Counts> selectBookCounts(@Param("bookIds") List<Long> bookIds);

    @Select("""
        SELECT node_id AS id, COUNT(*) AS item_count,
               SUM(CASE WHEN kind = 'question' THEN 1 ELSE 0 END) AS question_count
        FROM biz_shelf_item WHERE book_id = #{bookId} GROUP BY node_id
        """)
    List<ShelfReadVo.Counts> selectNodeCounts(@Param("bookId") Long bookId);

    /** Called only with question IDs from a validated page of at most 100 shelf items. */
    @Select("""
        <script>
        SELECT q.id, q.question_type, q.difficult, q.subject_id,
               CASE WHEN q.import_source = 'misikt' THEN stem.content
                    ELSE COALESCE(stem.content, q.stem_text) END AS stem_text,
               stem.content AS stem_text_content, q.stem_img_url AS stem_img,
               COALESCE(answer.content, (SELECT t.content FROM biz_text_content t
                   WHERE t.question_id = q.id AND t.content_type = 'A' ORDER BY t.id DESC LIMIT 1))
                   AS answer_text_content,
               COALESCE(analysis.content, (SELECT t.content FROM biz_text_content t
                   WHERE t.question_id = q.id AND t.content_type = 'E' ORDER BY t.id DESC LIMIT 1))
                   AS analyze_text_content,
               q.answer_img_url AS answer_img, q.explain_img_url AS explain_img,
               block.block_json, block.answer_block_json, block.analyze_block_json
        FROM biz_question q
        LEFT JOIN biz_text_content stem ON stem.id = q.stem_text_content_id
        LEFT JOIN biz_text_content answer ON answer.id = q.answer_text_content_id
        LEFT JOIN biz_text_content analysis ON analysis.id = q.analyze_text_content_id
        LEFT JOIN biz_question_block block ON block.question_id = q.id
        WHERE q.status != '2' AND q.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<ShelfReadVo.Question> selectReadingQuestions(@Param("ids") List<Long> ids);
}
