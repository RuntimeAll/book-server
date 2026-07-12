package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.SpecialNode;

/**
 * 专项目录节点 Mapper（biz_shelf_node）。PRD-003 C 位自建薄 Mapper。
 *
 * @author codeplace-C PRD-003
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SpecialNodeMapper extends BizBaseMapper<SpecialNode> {
}
