package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionFolder;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 题目收藏夹 Mapper（biz_question_folder）。
 *
 * <p>🔴 表无 tenant_id 字段，类级 {@code @InterceptorIgnore(tenantLine = "true")} 挡掉
 * Wrapper 式自定义方法的多租户注入；但 BaseMapper 继承方法（selectById / updateById）的
 * mappedStatementId namespace 落在 BaseMapper 命中不到本注解（PRD-A-005 G4 教训），
 * 故 Service 端涉及继承方法的调用再叠 TenantHelper.ignore 线程级包裹双保险。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionFolderMapper extends BaseMapperPlus<BizQuestionFolder, BizQuestionFolder> {
}
