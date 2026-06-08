package org.dromara.book.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.vo.QuestionItemVo;

import java.util.Collection;
import java.util.List;

/**
 * 题目主表 Mapper（biz_question）。
 *
 * <p>page 走 mapper.xml 自定义 SQL — 走 misikt 字段命名（stemImg 等），过滤条件由 Wrapper 注入。
 * biz_question 表无 tenant_id 字段，关 MyBatis-Plus 多租户拦截器自动注入。
 *
 * @author backend-dev
 */
@Mapper
public interface BizQuestionMapper extends BizBaseMapper<BizQuestion> {

    /**
     * 分页查询题目列表（不含 questionKnowledges，由 Service 二次填充）。
     *
     * <p>J 卡段②：新增 {@code currentUserId} 参数，mapper.xml LEFT JOIN biz_question_favorite
     * 一次性带出 isFavorite 字段，免 FE N+1 调用 /qd/favorite/{id}。
     *
     * @param page           MyBatis-Plus 分页对象
     * @param wrapper        LambdaQueryWrapper / QueryWrapper 注入 WHERE 条件
     * @param currentUserId  当前登录用户 ID（Service 注入；未登录场景理论上 SaCheckLogin 拦在前面，此处不容 null）
     * @return 分页 VO（仅 BizQuestion 主字段映射 + 别名 stemImg 等 + isFavorite）
     */
    IPage<QuestionItemVo> selectQuestionPage(IPage<QuestionItemVo> page,
                                             @Param(Constants.WRAPPER) Wrapper<BizQuestion> wrapper,
                                             @Param("currentUserId") Long currentUserId);

    /**
     * PRD-A-005 T5 — 收藏分页查询（GET /teacher/qd/favorite/page）。
     *
     * <p>INNER JOIN biz_question_favorite 只出 {@code currentUserId} 已收藏题（防越权）；
     * folderId 非空再按收藏夹过滤。复用 {@link QuestionItemVo}（与题库 page 同一 VO，方便 FE 复用 QuestionCard）。
     * is_favorite 恒 1。knowledges / freeTags 由 Service 二次填充（与 page 同款）。
     *
     * @param page          MyBatis-Plus 分页对象
     * @param currentUserId 当前登录用户 ID（Service 注入；@SaCheckLogin 兜底，不容 null）
     * @param folderId      收藏夹 ID（可空，null 时不按夹过滤）
     * @return 分页 VO（QuestionItemVo，is_favorite 恒 1）
     */
    IPage<QuestionItemVo> selectFavoritePage(IPage<QuestionItemVo> page,
                                             @Param("currentUserId") Long currentUserId,
                                             @Param("folderId") Long folderId);

    /**
     * 单题详情查询（不含 questionKnowledges / questionStdKnowledges，由 Service 二次填充）。
     *
     * @param id 题目 ID
     * @return 详情 VO（含 answer/explain/file/video 等详情字段）
     */
    org.dromara.book.domain.vo.QuestionDetailVo selectQuestionDetailById(@Param("id") Long id);

    /**
     * Q' 卡段① — 批量按 id 拉详情（试卷预览 PDF 导出场景）。
     *
     * <p>软删过滤：{@code status<>'2'}。不带 ORDER BY（Service 端按入参顺序重排）。
     * 不含 questionKnowledges / questionStdKnowledges / freeTags（由 Service 二次填充）。
     *
     * @param ids 题目 ID 集合（不可空）
     * @return 详情 VO 列表（顺序不保证，由 Service LinkedHashMap by id 重排）
     */
    List<org.dromara.book.domain.vo.QuestionDetailVo> selectQuestionDetailByIds(@Param("ids") Collection<Long> ids);
}
