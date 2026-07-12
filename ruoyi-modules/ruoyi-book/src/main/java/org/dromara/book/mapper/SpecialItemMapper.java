package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.SpecialItem;

/**
 * 专项内容项 Mapper（biz_shelf_item）。PRD-003 C 位自建薄 Mapper。
 *
 * <p>nodeId 批量入专项（pick 的 nodeId 分支）复用本 Mapper 读源书节点下 kind=question 的 item。
 *
 * @author codeplace-C PRD-003
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SpecialItemMapper extends BizBaseMapper<SpecialItem> {
}
