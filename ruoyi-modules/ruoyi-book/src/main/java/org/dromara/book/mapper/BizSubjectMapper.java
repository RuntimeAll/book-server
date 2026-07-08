package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.book.domain.entity.BizSubject;

import java.util.List;

/**
 * 章节-知识点树 Mapper（biz_subject）。
 *
 * <p>biz_* 表跟租户解耦（misikt 业务无多租户场景），@InterceptorIgnore 关 MyBatis-Plus
 * TenantLineInnerInterceptor 自动注入的 tenant_id where（biz_subject 表无 tenant_id 字段）。
 *
 * @author backend-dev
 */
@Mapper
public interface BizSubjectMapper extends BizBaseMapper<BizSubject> {

    /**
     * 取某学科根（level1，如 100/200/901）子树的全部节点（含根本身）——PRD-A-024 批2 KG 锚定。
     *
     * <p>MySQL 8 递归 CTE 顺 parent_id 展开；返回 id/parent_id/name/level，调用方内存判叶子 + 名字匹配。
     * biz_subject 约 3.4k 行、单学科子树至多几百节点，一次查全无压力。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE sub AS (
            SELECT id, parent_id, name, level FROM biz_subject WHERE id = #{rootId}
            UNION ALL
            SELECT s.id, s.parent_id, s.name, s.level
            FROM biz_subject s JOIN sub ON s.parent_id = sub.id
        )
        SELECT id, parent_id, name, level FROM sub
        """)
    List<BizSubject> selectSubtree(@Param("rootId") String rootId);
}
