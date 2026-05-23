package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizPaperSection;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 试卷题目分组 Mapper（biz_paper_section） — Q 卡 createExamPaper 端点写入用。
 *
 * <p>仅承担 Q 卡 INSERT 默认 section。后续 V1.5 / R 卡如需 section CRUD 在此扩展。
 *
 * <p>🔴 必须 @InterceptorIgnore — biz_paper_section 无 tenant_id 列（同 BizPaperMapper / BizPaperQuestionMapper）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizPaperSectionMapper extends BaseMapperPlus<BizPaperSection, BizPaperSection> {
}
