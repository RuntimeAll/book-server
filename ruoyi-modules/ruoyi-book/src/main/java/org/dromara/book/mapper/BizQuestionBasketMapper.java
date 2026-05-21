package org.dromara.book.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.entity.BizQuestionBasket;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 试题筐 Mapper（biz_question_basket）。
 *
 * <p>定制 SQL（走 mapper.xml）：
 * <ul>
 *   <li>{@link #insertIgnore} — INSERT IGNORE 防重（复合主键 user_id+question_id）</li>
 *   <li>{@link #deleteByUserAndQuestion} — 单题物理删</li>
 *   <li>{@link #deleteAllByUser} — 清空当前用户全筐</li>
 *   <li>{@link #countByUser} — 角标数量</li>
 *   <li>{@link #selectBasketQuestionsByUser} — JOIN biz_question 拉整套题目字段（复用 page ResultMap）</li>
 * </ul>
 *
 * @author backend-dev
 */
@Mapper
public interface BizQuestionBasketMapper extends BaseMapperPlus<BizQuestionBasket, BizQuestionBasket> {

    /**
     * INSERT IGNORE — 复合主键命中则忽略（防重加筐，Iron law §1.6）。
     *
     * @param userId     用户 ID
     * @param questionId 题目 ID
     * @return 影响行数（0=已存在被忽略，1=新增）
     */
    int insertIgnore(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * 物理删单题（用户态数据可物理删，Iron law §1.6）。
     *
     * @param userId     用户 ID
     * @param questionId 题目 ID
     * @return 影响行数（0=未找到，1=删除）
     */
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * 清空当前用户全部筐题。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int deleteAllByUser(@Param("userId") Long userId);

    /**
     * 统计当前用户筐题数量（角标用）。
     *
     * @param userId 用户 ID
     * @return 数量
     */
    long countByUser(@Param("userId") Long userId);

    /**
     * 拉当前用户全部筐题（JOIN biz_question 取整套题目字段；按 add_time 倒序）。
     *
     * <p>仅返已发布题（biz_question.status='1'）— 软删 / 草稿不在筐内可见列表。
     * 不返 questionKnowledges，由 Service 二次填充（复用 page 端点的 batch load 策略）。
     *
     * @param userId 用户 ID
     * @return 题目 VO 列表
     */
    List<QuestionItemVo> selectBasketQuestionsByUser(@Param("userId") Long userId);
}
