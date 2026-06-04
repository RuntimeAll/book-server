package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizTextContent;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 题目三要素长文本外置 Mapper（biz_text_content）— PRD-B-006 收尾增量。
 *
 * <p>无 tenant_id 列；关闭 MP 多租户拦截器自动注入，避免对老 biz_* 表族踩 FE Bug
 * (踩坑见 feedback_ruoyi_basemapper_tenant_ignore 记忆)。
 *
 * <p>使用方式：admin 写题/编辑题时 upsert (question_id + content_type 唯一)，
 * 教师端详情查询时 LEFT JOIN 拼回 explain / answer 文本。
 *
 * @author backend-dev (PRD-B-006)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizTextContentMapper extends BaseMapperPlus<BizTextContent, BizTextContent> {
}
