package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizPaperCategory;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 试卷分类树 Mapper（biz_paper_category）— D 卡卷库视觉级还原。
 *
 * <p>biz_* 表跟租户解耦（misikt 业务无多租户场景），@InterceptorIgnore 关 MyBatis-Plus
 * TenantLineInnerInterceptor 自动注入的 tenant_id where（biz_paper_category 表无 tenant_id 字段）。
 *
 * <p>D 卡 V0.5 只读使用：service 走 selectList(null) 拉全表 + 内存建树。
 * 97 行的表全拉无性能压力。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizPaperCategoryMapper extends BaseMapperPlus<BizPaperCategory, BizPaperCategory> {
}
