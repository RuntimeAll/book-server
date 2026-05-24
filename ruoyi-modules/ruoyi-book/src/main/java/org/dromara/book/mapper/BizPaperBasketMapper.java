package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.book.domain.entity.BizPaperBasket;
import org.dromara.book.domain.vo.PaperListItemVo;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.util.List;

/**
 * 试卷筐 Mapper（biz_paper_basket） — R 卡段① 对称试题筐复刻。
 *
 * <p>定制 SQL（走 mapper.xml）：
 * <ul>
 *   <li>{@link #insertIgnore} — INSERT IGNORE 防重（复合主键 user_id+paper_id）</li>
 *   <li>{@link #deleteByUserAndPaper} — 单卷物理删</li>
 *   <li>{@link #deleteAllByUser} — 清空当前用户全筐</li>
 *   <li>{@link #countByUser} — 角标数量</li>
 *   <li>{@link #selectBasketPapersByUser} — JOIN biz_paper 拉 PaperListItemVo（复刻 PaperListItemMap 字段映射）</li>
 * </ul>
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_paper_basket / biz_paper
 * 无 tenant_id 列，否则多租户拦截器拼 {@code AND tenant_id = ?} 报 SQLSyntaxErrorException
 * （沿用 BizPaperMapper / BizQuestionBasketMapper 同款防御）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizPaperBasketMapper extends BaseMapperPlus<BizPaperBasket, BizPaperBasket> {

    /**
     * INSERT IGNORE — 复合主键 (user_id, paper_id) 命中则忽略（防重加筐，Iron law §1.6）。
     *
     * @param userId  用户 ID
     * @param paperId 试卷 ID
     * @return 影响行数（0=已存在被忽略，1=新增）
     */
    int insertIgnore(@Param("userId") Long userId, @Param("paperId") Long paperId);

    /**
     * 物理删单卷（用户态数据可物理删，Iron law §1.6）。
     *
     * @param userId  用户 ID
     * @param paperId 试卷 ID
     * @return 影响行数（0=未找到，1=删除）
     */
    int deleteByUserAndPaper(@Param("userId") Long userId, @Param("paperId") Long paperId);

    /**
     * 清空当前用户全部筐卷。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int deleteAllByUser(@Param("userId") Long userId);

    /**
     * 统计当前用户筐卷数量（角标用）。
     *
     * @param userId 用户 ID
     * @return 数量
     */
    long countByUser(@Param("userId") Long userId);

    /**
     * 拉当前用户全部筐卷（JOIN biz_paper 取卷头字段；按 add_time 倒序）。
     *
     * <p>仅返已发布卷（biz_paper.status='1'）— 软删 / 草稿不在筐内可见列表。
     * 字段映射 / CAST 口径完全对齐 {@code BizPaperMapper.xml} 的 PaperListItemMap
     * （score/hg_score/status/create_by 强转 Integer 对齐 misikt 真响应字节级口径）。
     *
     * @param userId 用户 ID
     * @return 试卷 VO 列表
     */
    List<PaperListItemVo> selectBasketPapersByUser(@Param("userId") Long userId);
}
