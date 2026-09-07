package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface PaperClassificationMapper {
    @Select("""
        <script>
        SELECT id, subject_id FROM biz_shelf_book WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<BookSubject> selectBookSubjects(@Param("ids") List<Long> ids);

    // Follow parent relations, not encoded ID prefixes; cap traversal for malformed trees.
    @Select("""
        <script>
        WITH RECURSIVE ancestors AS (
            SELECT id AS source_id, id, parent_id, subject, stage, grade, volume, 0 AS depth
            FROM biz_subject WHERE status = '0' AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            UNION ALL
            SELECT a.source_id, s.id, s.parent_id, s.subject, s.stage, s.grade, s.volume, a.depth + 1
            FROM ancestors a JOIN biz_subject s ON s.id = a.parent_id
            WHERE a.depth &lt; 16 AND a.grade IS NULL AND s.status = '0'
        )
        SELECT source_id, subject, stage, grade, volume FROM ancestors WHERE grade IS NOT NULL
        </script>
        """)
    List<SubjectDimensions> selectSubjectDimensions(@Param("ids") List<String> ids);

    @Data
    class BookSubject {
        private Long id;
        private String subjectId;
    }

    @Data
    class SubjectDimensions {
        private String sourceId;
        private Integer subject;
        private Integer stage;
        private Integer grade;
        private Integer volume;
    }
}
