package org.dromara.bookadmin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * biz_free_tag 字典 + biz_question_free_tag 关联表的写 Mapper（H1 卡段② BE 波 2b — V-6）。
 *
 * <p><strong>为什么不复用 ruoyi-book 的 mapper：</strong>
 * 用户 2026-05-22 拍板模块隔离铁则 — admin 改动禁碰 {@code ruoyi-book/} 模块。
 * 教师端 {@code BizQuestionFreeTagMapper} 不继承 BaseMapperPlus，只有读方法，
 * 没有 insert/delete；且 biz_free_tag 在 ruoyi-book 模块根本没有 entity 也没有 mapper
 * （X 卡段② 只做读跳过造），所以本写 Mapper 必须在 admin 模块独立新建。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_question_free_tag
 * + biz_free_tag 业务表都无 tenant_id 列（B 卡 T7 教训沉淀）。
 *
 * <p>本 Mapper 不绑 BaseMapperPlus（无 entity 类）— mapper.xml 落
 * {@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/AdminFreeTagWriteMapper.xml}。
 *
 * @author backend-dev (H1 卡段② BE 波 2b)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminFreeTagWriteMapper {

    /**
     * 按 name 查 biz_free_tag.id（unique 约束）。
     *
     * @param name tag 文本
     * @return tag id 或 null（不存在）
     */
    Long selectIdByName(@Param("name") String name);

    /**
     * 新增 biz_free_tag 字典行（name unique + use_count 头期宽松 +1）。
     *
     * <p>头期策略（PRD §6 R5）：INSERT 字典时 use_count=1，DELETE 关联时不减，
     * 误差可接受沉远期 cron 重算。
     *
     * <p>调用方拿不到自增 id（XML 端不绑 entity）— 紧跟着调
     * {@link #selectIdByName(String)} 再查一次拿 id（unique 约束保证一致性）。
     *
     * @param name tag 文本
     * @return 影响行数（1 = 新建成功）
     */
    int insertFreeTag(@Param("name") String name);

    /**
     * 按 question_id 全量删除关联表（biz_question_free_tag）— 编辑前清旧再批量写新。
     *
     * @param questionId 题目 ID
     * @return 影响行数
     */
    int deleteByQuestionId(@Param("questionId") Long questionId);

    /**
     * 新增单条 biz_question_free_tag 关联（question_id + tag_id + position）。
     *
     * @param questionId 题目 ID
     * @param tagId      biz_free_tag.id
     * @param position   同题内位置（0 起算，决定 FE 颜色）
     * @return 影响行数
     */
    int insertRel(@Param("questionId") Long questionId,
                  @Param("tagId") Long tagId,
                  @Param("position") int position);

    /**
     * 按 keyword 模糊搜索 biz_free_tag 字典，按 use_count 倒序返热度 top N（H1 卡 Bug2 补丁）。
     *
     * <p>FE 编辑页标签从动态输入改成搜索式 multi-select 后，需此端点提供"输入即搜"
     * 候选下拉列表。keyword 为空 / null 时返热门 top N（用 use_count 倒序），
     * 非空时走 LIKE '%keyword%' 模糊匹配。
     *
     * <p>SQL 行为：
     * <ul>
     *   <li>keyword null / "" → 不加 name 过滤，纯热度排序</li>
     *   <li>keyword 非空 → 加 LIKE '%keyword%' 过滤后再按 use_count 倒序</li>
     *   <li>ORDER BY use_count DESC, id DESC（同热度按 id 兜底稳定排序）</li>
     *   <li>LIMIT 由调用方传（默认 20，service 层 clamp 上限 100）</li>
     * </ul>
     *
     * <p>返结构：{@code [{id: Long, name: String, useCount: Integer}, ...]}
     * — 字段名走 mapper.xml 显式 alias（{@code use_count AS useCount}）。
     *
     * @param keyword 关键字（null / 空 = 不过滤，返热门 top N）
     * @param limit   返回上限（service 层兜底 20，最大 100）
     * @return tag 候选列表（按 use_count 倒序）
     */
    java.util.List<java.util.Map<String, Object>> selectListByKeyword(@Param("keyword") String keyword,
                                                                      @Param("limit") int limit);
}
