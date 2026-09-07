package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface QuestionSelectionMapper {
    @Select("""
        <script>
        SELECT id FROM biz_question WHERE status = '1' AND id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Long> selectAvailableQuestionIds(@Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT q.id FROM biz_question q
        WHERE q.status = '1' AND q.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        AND (q.is_public = 1 OR q.create_user = #{userId}
          OR (q.create_user IS NULL AND q.create_by = #{userId}) OR #{superAdmin} = TRUE)
        </script>
        """)
    List<Long> selectReadableQuestionIds(@Param("ids") List<Long> ids, @Param("userId") Long userId,
                                       @Param("superAdmin") boolean superAdmin);

    @Select("""
        <script>
        SELECT i.id, i.book_id, i.question_id, i.kind, i.override_json,
               b.owner_id, b.is_public, b.status AS book_status
        FROM biz_shelf_item i JOIN biz_shelf_book b ON b.id = i.book_id
        WHERE i.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<ShelfSelectionRow> selectShelfItems(@Param("ids") List<Long> ids);

    @Data
    class ShelfSelectionRow {
        private Long id;
        private Long bookId;
        private Long questionId;
        private String kind;
        private String overrideJson;
        private Long ownerId;
        private Integer isPublic;
        private String bookStatus;
    }
}
