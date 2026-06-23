package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 题目主表实体（biz_question）。
 *
 * <p>字段映射要点（misikt JSON ↔ DB 列 ↔ Java 字段）：
 * <ul>
 *   <li>DB {@code stem_img_url / answer_img_url / explain_img_url / file_bin_url} 加 {@code _url} 后缀，
 *       Java 字段对应 {@code stemImgUrl} 等驼峰，VO 输出去 {@code _url} 后缀为 {@code stemImg}（misikt 风格）</li>
 *   <li>DB {@code create_time DATETIME} → Java {@code Date createTime} → VO {@code Long createTime} ms timestamp（归一化）</li>
 *   <li>DB {@code status CHAR(1)}：'0' 草稿 / '1' 已发布 / '2' 软删</li>
 * </ul>
 *
 * <p>🔴 PRD-B-013 减法：删除 10 死字段（详见 PRD-B-013 §scope.A — biz_question 段），
 * 均为 misikt 复刻期残留 + 错设计字段，N1 录题地基清理。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_question")
public class BizQuestion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目 ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 题型：1=选择 / 4=填空 / 5=简答（misikt 真实只 3 种；DB 字典含 2 填空 3 判断保留备用）
     */
    private Integer questionType;

    /**
     * 难度 1-4 星（注意字段名是 difficult，不是 difficulty）
     */
    private Integer difficult;

    /**
     * 章节-知识点编码（biz_subject.id 任意层级）
     */
    private String subjectId;

    /**
     * 题干文本（LaTeX 源 / 纯文本）— PRD-B-006 后保留双写过渡，不 DROP；
     * 长文本"外置"事实来源走 {@link #stemTextContentId} -> biz_text_content.content。
     */
    private String stemText;

    /**
     * 题干文本外置 FK -> biz_text_content.id（PRD-B-006 收尾增量新增）。
     *
     * <p>非空时优先以 biz_text_content.content 为事实来源；为 null 时降级走 {@link #stemText}（兼容旧数据）。
     * V9 ETL 已把 29233 行旧 stem_text 迁出并回填本 FK。
     */
    private Long stemTextContentId;

    /**
     * 题干图 URL（DB 列 stem_img_url，VO 输出 stemImg 去 _url 后缀）
     */
    @TableField("stem_img_url")
    private String stemImgUrl;

    /**
     * 答案图 URL（DB 列 answer_img_url，VO 输出 answerImg）
     */
    @TableField("answer_img_url")
    private String answerImgUrl;

    /**
     * 解析图 URL（DB 列 explain_img_url，VO 输出 explainImg）
     */
    @TableField("explain_img_url")
    private String explainImgUrl;

    /**
     * 笔迹数据 URL（DB 列 file_bin_url，VO 输出 fileBin）
     */
    @TableField("file_bin_url")
    private String fileBinUrl;

    /**
     * 答案文本外置 FK -> biz_text_content.id（PRD-B-006 收尾增量新增）。
     *
     * <p>大块答案文本（解题步骤 / 长答案）落 biz_text_content content_type='A'；
     * 短答案（A/B/C/D / 短填空）历史走 biz_question 短答案列（PRD-B-013 已删）。
     */
    private Long answerTextContentId;

    /**
     * 出处年份
     */
    private String examYear;

    /**
     * 出处试卷 ID
     */
    private Long examPaperId;

    /**
     * 出处试卷名
     */
    private String examPaperName;

    /**
     * 解析文本外置 FK -> biz_text_content.id（misikt 抓包遗留字段，
     * PRD-B-006 后正式启用 — content_type='E' 解析）。
     */
    private Long analyzeTextContentId;

    /**
     * 题目格式版本码（默认 1010）
     */
    private Integer version;

    /**
     * 状态 CHAR(1)：'0' 草稿 / '1' 已发布 / '2' 软删
     */
    private String status;

    /**
     * 是否公开 0/1（PRD-A-023 B10 公共题库审核闸）：
     * 0=私有/未公开（默认，入库即此态，仅「我的题库」可见）；1=超管审核通过公开（进公共题库）。
     *
     * <p>🔴「入库 ≠ 公开」：status='1' 仅代表已入库；公共题库查询额外要求 is_public=1。
     * create/promote 路径不动本列（默认 0），仅 {@code setPublic}（超管专属）置 1。
     */
    private Integer isPublic;

    private String createBy;

    /**
     * 创建用户 BIGINT（跟 create_by VARCHAR 历史并存）
     */
    private Long createUser;

    /**
     * 基础分值 DECIMAL(5,2)（PRD-B-012 标注维度地基新增）
     */
    private BigDecimal baseScore;

    /**
     * 是否已收藏 0/1（PRD-B-012 新增）
     */
    private Integer isCollected;

    /**
     * 导入来源（PRD-B-012 新增）
     */
    private String importSource;

    /**
     * 导入批次 ID（PRD-B-012 新增）
     */
    private String importBatchId;

    /**
     * 区域编码（PRD-B-012 新增）
     */
    private String regionCode;

    /**
     * 来源类型（PRD-B-012 新增）
     */
    private Integer sourceType;

    /**
     * 母题 ID（变式关系 PRD-B-012 新增）
     */
    private Long motherQuestionId;

    /**
     * 变式关系（PRD-B-012 新增）
     */
    private String variantRelation;

    /**
     * 标注版本（PRD-B-012 新增）
     */
    private Integer annotateVersion;

    /**
     * 标注状态（PRD-B-012 新增）
     */
    private Integer annotateStatus;

    /**
     * ①知识点 ID（关联现有知识点树）—— PRD-C-006 V16 新增，AI 打标维度 1。
     */
    private String dim1KpId;

    /**
     * ②题型 1选择/4填空/5解答/6证明 —— V16 新增（dim2_qtype TINYINT），AI 打标维度 2。
     */
    private Integer dim2Qtype;

    /**
     * ④难度 1-4（dim4_difficulty TINYINT）—— V16 新增，AI 打标维度 4。
     */
    private Integer dim4Difficulty;

    /**
     * ⑤图形/情境结构指纹（dim5_structure VARCHAR(255)）—— V16 新增，AI 打标维度 5。
     */
    private String dim5Structure;

    /**
     * AI 打标状态机（label_status TINYINT）：0未标 / 1AI已标 / 2已审核 / 3争议 —— V16 新增。
     */
    private Integer labelStatus;

    /**
     * AI 自评置信度 0-1（label_confidence DECIMAL(4,3)）—— V16 新增。
     */
    private BigDecimal labelConfidence;

    /**
     * AI 模型名或人员（labeled_by VARCHAR(64)）—— V16 新增。
     */
    private String labeledBy;

    /**
     * 打标时间（labeled_at DATETIME）—— V16 新增，update_label 服务端取 now。
     */
    private Date labeledAt;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private String remark;
}
