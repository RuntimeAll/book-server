package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.domain.entity.BizQuestionBasketEntry;
import org.dromara.book.domain.bo.QuestionBasketBo;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface QuestionBasketEntryMapper extends BizBaseMapper<BizQuestionBasketEntry> {
    @Delete("""
        <script>
        DELETE FROM biz_question_basket_entry WHERE user_id = #{userId} AND namespace = #{namespace}
        AND (<foreach collection="entries" item="entry" separator=" OR ">
          (id = #{entry.basketEntryId} AND entry_key = #{entry.entryKey})
        </foreach>)
        </script>
        """)
    int deleteVersions(@Param("userId") Long userId, @Param("namespace") String namespace,
                       @Param("entries") List<QuestionBasketBo.EntryVersion> entries);

    @Select("""
        SELECT entry_key FROM biz_question_basket_entry
        WHERE user_id = #{userId} AND namespace = #{namespace}
        ORDER BY id ASC LIMIT #{limit}
        """)
    List<String> selectEntryKeys(@Param("userId") Long userId, @Param("namespace") String namespace,
                                 @Param("limit") int limit);

    @Insert("""
        INSERT INTO biz_question_basket_scope(user_id, namespace) VALUES (#{userId}, #{namespace})
        ON DUPLICATE KEY UPDATE user_id = user_id
        """)
    void ensureScope(@Param("userId") Long userId, @Param("namespace") String namespace);

    @Select("""
        SELECT user_id FROM biz_question_basket_scope
        WHERE user_id = #{userId} AND namespace = #{namespace} FOR UPDATE
        """)
    Long lockScope(@Param("userId") Long userId, @Param("namespace") String namespace);
}
