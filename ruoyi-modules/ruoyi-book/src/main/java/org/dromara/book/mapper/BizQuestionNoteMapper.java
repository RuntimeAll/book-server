package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.book.domain.entity.BizQuestionNote;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 题目个人备注 Mapper（biz_question_note）。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — 业务表无 tenant_id 字段，
 * 否则脚手架数据隔离拦截器会拼 {@code AND tenant_id = ?} 撞 SQLSyntaxErrorException
 * (B 卡 T7 教训沉淀)。
 *
 * <p>本 mapper 不需要自定义 SQL — GET / upsert 全走 LambdaQueryWrapper + BaseMapperPlus 的
 * selectOne / insert / updateById 即可：
 * <ul>
 *   <li>GET    → selectOne(eq user_id, eq question_id)（null 即未写过笔记）</li>
 *   <li>POST   → selectOne 判存在；存在 → updateById；不存在 → insert（upsert）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizQuestionNoteMapper extends BaseMapperPlus<BizQuestionNote, BizQuestionNote> {
}
