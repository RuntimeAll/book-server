package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.domain.vo.ShelfReadVo;

import java.util.List;

/**
 * 书架·目录节点 Mapper（biz_shelf_node，PRD-002）。
 *
 * <p>🔴 {@code @InterceptorIgnore(tenantLine="true")} 必须直挂（biz_* 表无 tenant_id，注解不随父接口继承）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizShelfNodeMapper extends BizBaseMapper<BizShelfNode> {
    @Select("""
        <script>
        SELECT book_id AS id, COUNT(*) AS node_count
        FROM biz_shelf_node WHERE book_id IN
        <foreach collection="bookIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY book_id
        </script>
        """)
    List<ShelfReadVo.Counts> selectBookCounts(@Param("bookIds") List<Long> bookIds);
}
