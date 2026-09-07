package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.book.domain.entity.BizPaperCreateRequest;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface PaperCreateRequestMapper {
    @Insert("""
        INSERT INTO biz_paper_create_request(user_id, request_id, payload_hash)
        VALUES (#{userId}, #{requestId}, #{payloadHash})
        ON DUPLICATE KEY UPDATE user_id = user_id
        """)
    void ensureRequest(@Param("userId") Long userId, @Param("requestId") String requestId,
                       @Param("payloadHash") String payloadHash);

    @Select("""
        SELECT user_id, request_id, payload_hash, paper_id, question_count
        FROM biz_paper_create_request WHERE user_id = #{userId} AND request_id = #{requestId} FOR UPDATE
        """)
    BizPaperCreateRequest lockRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Update("""
        UPDATE biz_paper_create_request SET paper_id = #{paperId}, question_count = #{questionCount}
        WHERE user_id = #{userId} AND request_id = #{requestId}
        """)
    int complete(@Param("userId") Long userId, @Param("requestId") String requestId,
                 @Param("paperId") Long paperId, @Param("questionCount") int questionCount);
}
