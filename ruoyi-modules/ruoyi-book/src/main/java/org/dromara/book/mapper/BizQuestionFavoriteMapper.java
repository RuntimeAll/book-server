package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionFavorite;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 题目收藏 Mapper（biz_question_favorite）。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — 业务表无 tenant_id 字段，
 * 否则脚手架数据隔离拦截器会拼 {@code AND tenant_id = ?} 撞 SQLSyntaxErrorException
 * (B 卡 T7 教训沉淀)。
 *
 * <p>本 mapper 不需要自定义 SQL — toggle / GET / DELETE 全走 LambdaQueryWrapper +
 * BaseMapperPlus 的 selectOne / insert / delete 即可：
 * <ul>
 *   <li>GET     → selectCount(eq user_id, eq question_id) > 0</li>
 *   <li>POST    → selectOne(eq user_id, eq question_id) 判存在；存在 → delete by id；不存在 → insert</li>
 *   <li>DELETE  → delete(eq user_id, eq question_id)（幂等，0/1 行都返成功）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionFavoriteMapper extends BaseMapperPlus<BizQuestionFavorite, BizQuestionFavorite> {
}
