package org.dromara.book.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * biz_free_tag 字典 + biz_question_free_tag 关联表的写 Mapper（PRD-C-014 B1，ruoyi-book 自有）。
 *
 * <p>🔴 模块隔离：ruoyi-book 不得依赖 ruoyi-book-admin，故照抄 admin 的
 * {@code AdminFreeTagWriteMapper} 写法，在 ruoyi-book 内独立新建（namespace 不同）。
 * ruoyi-book 原 {@link BizQuestionFreeTagMapper} 只读不写，标签三轨写入需本 Mapper。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} —— biz_free_tag /
 * biz_question_free_tag 业务表都无 tenant_id 列。
 *
 * <p>本 Mapper 不绑 entity（XML 端不映射 entity）—— XML 落
 * {@code ruoyi-book/src/main/resources/mapper/book/BizFreeTagWriteMapper.xml}。
 *
 * @author backend-dev (PRD-C-014 B1)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BizFreeTagWriteMapper {

    /**
     * 按 name exact 查 biz_free_tag.id（name unique）。
     *
     * @param name tag 文本
     * @return tag id 或 null（不存在）
     */
    Long selectIdByName(@Param("name") String name);

    /**
     * 新增 biz_free_tag 字典行（use_count=1）。调用方拿不到自增 id —
     * 紧跟着调 {@link #selectIdByName} 再查一次拿 id（name unique 保证一致）。
     *
     * @param name tag 文本
     * @return 影响行数（1=新建成功）
     */
    int insertFreeTag(@Param("name") String name);

    /**
     * 既有标签命中 → use_count + 1（复用计数）。
     *
     * @param id biz_free_tag.id
     * @return 影响行数
     */
    int incrementUseCount(@Param("id") Long id);

    /**
     * 新增单条 biz_question_free_tag 关联（question_id + tag_id + position）。
     *
     * @param questionId 题目 ID
     * @param tagId      biz_free_tag.id
     * @param position   同题内位置（0 起算，数组下标，决定 FE 颜色）
     * @return 影响行数
     */
    int insertRel(@Param("questionId") Long questionId,
                  @Param("tagId") Long tagId,
                  @Param("position") int position);

    /**
     * 查某题某 tag 是否已挂接（防重：B3 单题入库防重依赖）。
     *
     * @param questionId 题目 ID
     * @param tagId      biz_free_tag.id
     * @return 命中条数（>0=已存在）
     */
    int countRel(@Param("questionId") Long questionId,
                 @Param("tagId") Long tagId);

    /**
     * T4 —— 某知识点（kpId）下高频标签候选池：
     * biz_question_free_tag ⨝ biz_question_knowledge（同 question_id 且 knowledge_id=kpId）⨝ biz_free_tag，
     * 按 tag 聚合 count desc limit N。
     *
     * <p>返结构：{@code [{id: Long, name: String, count: Long}, ...]}，按 count 倒序。
     * 空结果返空 list。
     *
     * @param kpId  知识点 ID（biz_subject.id，biz_question_knowledge.knowledge_id 为 varchar）
     * @param limit 返回上限（service 兜底 300，clamp 上限）
     * @return 标签候选列表
     */
    List<Map<String, Object>> selectTagsByKp(@Param("kpId") String kpId,
                                             @Param("limit") int limit);
}
