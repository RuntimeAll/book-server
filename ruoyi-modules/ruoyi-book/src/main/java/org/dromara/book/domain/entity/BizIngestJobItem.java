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
 * 批量录题作业拆出题暂存实体（biz_ingest_job_item，V27，PRD-A-002 路B）。
 *
 * <p>一行 = 该作业 opus 拆出的一题（暂存）。老师在审核页勾选的才 {@code ingestQuestion} 进正式
 * biz_question（status='0' 草稿），没勾的随作业留存。正式题表只进确认题，不污染。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}；雪花 id（ASSIGN_ID）。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Data
@TableName("biz_ingest_job_item")
public class BizIngestJobItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：待审 */
    public static final String STATUS_PENDING = "pending";
    /** 状态：已入库 */
    public static final String STATUS_COMMITTED = "committed";
    /** 状态：已弃 */
    public static final String STATUS_DROPPED = "dropped";

    /** 拆出题ID（雪花） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属作业 biz_ingest_job.id */
    @TableField("job_id")
    private Long jobId;

    /** 题序 */
    @TableField("seq")
    private Integer seq;

    /** 题干（Markdown+$LaTeX$） */
    @TableField("stem_text")
    private String stemText;

    /** 题型 1选择/2填空/5解答 */
    @TableField("question_type")
    private Integer questionType;

    /** 选项数组（JSON） */
    @TableField("options_json")
    private String optionsJson;

    /** 答案（from_source/ai_solve 时有） */
    @TableField("answer_text")
    private String answerText;

    /** 解析 */
    @TableField("analyze_text")
    private String analyzeText;

    /** 含图 0/1 */
    @TableField("has_figure")
    private Integer hasFigure;

    /** 难度 1-3 */
    @TableField("difficulty")
    private Integer difficulty;

    /** 10维DNA（ai_solve 时，JSON） */
    @TableField("dna_json")
    private String dnaJson;

    /** sympy 验算 pass/fail/degrade */
    @TableField("verify_verdict")
    private String verifyVerdict;

    /** 待审软提示 0/1 */
    @TableField("need_review")
    private Integer needReview;

    /** pending待审/committed已入库/dropped已弃 */
    @TableField("item_status")
    private String itemStatus;

    /** 入库后回填 biz_question.id */
    @TableField("committed_question_id")
    private Long committedQuestionId;

    /**
     * 裁出题图（PRD-A-024 批1）：JSON 数组 [{"seq":1,"ossUrl":"...","bbox":[x1,y1,x2,y2],"conf":0.9,"assigned":true}]。
     * NULL=无图/未裁；assigned=false 表示整卷启发式未确信归属（图不丢，待审核页人工挂题）。
     */
    @TableField("figures_json")
    private String figuresJson;

    /**
     * KG 锚定结果（PRD-A-024 批2 scope⑤ / D10 三段式）：JSON 对象
     * {"kpId":"200003","kpName":"主考点名","matchedName":"匹配到的叶子名","stage":"exact|norm|contains|llm|fallback","confidence":0.95,"fallback":false}。
     * NULL=未锚（无 dna 主考点名或 stem_only 模式）；fallback=true 表示挂了学科综合节点（未命中具体考点）。
     * commit 时据此写 biz_question_knowledge(isPrimary=1,source='AI')；审核页据此展示锚点。
     */
    @TableField("kp_anchor_json")
    private String kpAnchorJson;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
