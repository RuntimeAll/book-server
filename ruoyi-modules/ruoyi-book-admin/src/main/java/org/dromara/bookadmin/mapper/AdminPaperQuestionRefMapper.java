package org.dromara.bookadmin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * biz_paper_question 引用关系 Mapper（H1 卡段② BE 波 2a — V-2 软删校验用）。
 *
 * <p><strong>为什么不复用 {@code BizPaperQuestionMapper}：</strong>
 * 用户 2026-05-22 拍板模块隔离铁则 — admin 改动禁碰 {@code ruoyi-book/} 模块。
 * 教师端 {@code BizPaperQuestionMapper} 只有 {@code selectPapersByQuestionId}，
 * 没有 count 方法。admin 需要的 count 在此处新建独立 Mapper。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — biz_paper_question 业务表
 * 无 tenant_id 列，否则脚手架数据隔离拦截器会拼 {@code AND tenant_id = ?} 撞
 * SQLSyntaxErrorException（B 卡 T7 教训沉淀，详见 ruoyi-book {@code BizPaperQuestionMapper} javadoc）。
 *
 * <p>本 Mapper 不绑 BaseMapperPlus（无 entity 类）— 只承担 count 查询，
 * mapper.xml 落 {@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/}（独立 namespace
 * 避开 {@code mapper/book/} 冲突）。
 *
 * @author backend-dev
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminPaperQuestionRefMapper {

    /**
     * 查询某题目被多少张试卷引用（V-2 软删校验）。
     *
     * <p>SQL：{@code SELECT COUNT(*) FROM biz_paper_question WHERE question_id = ?}。
     * 不区分 biz_paper.status — 历史所有试卷的引用都算（避免已发布卷的题被悄悄软删 → 试卷渲染时挂）。
     *
     * @param questionId 题目 ID
     * @return 引用次数（≥0）
     */
    int countByQuestionId(@Param("questionId") Long questionId);
}
