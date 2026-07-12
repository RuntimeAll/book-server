package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.SpecialBook;

/**
 * 专项书 Mapper（biz_shelf_book，book_type='special'）。PRD-003 C 位自建薄 Mapper。
 *
 * <p>🔴 类级 {@code @InterceptorIgnore(tenantLine="true")} 直挂（biz_* 无 tenant_id，注解不随父接口继承）。
 *
 * @author codeplace-C PRD-003
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SpecialBookMapper extends BizBaseMapper<SpecialBook> {
}
