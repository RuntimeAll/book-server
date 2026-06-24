package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目结构化网格块存储实体（biz_question_block，V23，PRD-A-015）。
 *
 * <p>一行 = 一道题的整份 block 文档（{@code question_id} 为 PK，一题一份）。把（个人）题目
 * 内容从「线性 Markdown 单字符串」升级为「结构化网格题块 JSON」。block_json 结构 =
 * PRD-A-015 §10.1 锁定 schema：{@code { v:1, rows:[ { cells:[ <text|image|option block> ] } ] }}。
 *
 * <p>渲染优先级（§10.2）：有 block_json → 用它（权威）；否则回落现有富文本/图片，旧
 * biz_text_content 语义不动。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}
 * （类级 {@code @InterceptorIgnore(tenantLine="true")}）+ application.yml tenant.excludes
 * 已加 biz_question_block（同 biz_export_record/biz_variant_upload 单租户约定）。
 *
 * <p>question_id 为逻辑 FK → biz_question.id（本工程惯例不建硬 FK 约束）。
 * 主键由调用方显式 set（= 题目 id），不走雪花/自增。
 *
 * @author backend-dev (PRD-A-015)
 */
@Data
@TableName("biz_question_block")
public class BizQuestionBlock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 = 题目 id（逻辑 FK→biz_question.id，一题一份 block 文档）。
     * IdType.INPUT — 由调用方显式 set，不自动生成。
     */
    @TableId(value = "question_id", type = IdType.INPUT)
    private Long questionId;

    /**
     * block 文档 JSON 原文（DB JSON 列，Java 侧存原始字符串）。
     * §10.1 schema：{@code {v,rows:[{cells:[block]}]}}。
     */
    @TableField("block_json")
    private String blockJson;

    /**
     * 答案 blockJson（C-204 统一富文本格式化层；走 convertRichText，三端同组件渲染）。null=回落旧富文本。
     */
    @TableField("answer_block_json")
    private String answerBlockJson;

    /**
     * 解析 blockJson（C-204；选项分析/小问/步骤拆块）。null=回落旧富文本。
     */
    @TableField("analyze_block_json")
    private String analyzeBlockJson;

    /**
     * schema 版本号（当前 1）
     */
    @TableField("v")
    private Integer v;

    /**
     * 最后保存者 sys_user.id（服务端 LoginHelper 强制，绝不信前端）
     */
    @TableField("update_by")
    private Long updateBy;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
