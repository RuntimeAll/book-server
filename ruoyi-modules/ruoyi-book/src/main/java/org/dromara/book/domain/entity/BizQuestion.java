package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
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
     * <p>大块答案文本（解题步骤 / 长答案）落 biz_text_content content_type='A'。
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
     * 自由标签（逗号分隔）
     */
    private String freeTag;

    /**
     * 题目格式版本码（默认 1010）
     */
    private Integer version;

    /**
     * 状态 CHAR(1)：'0' 草稿 / '1' 已发布 / '2' 软删
     */
    private String status;

    private String createBy;

    /**
     * 创建用户 BIGINT（跟 create_by VARCHAR 历史并存）
     */
    private Long createUser;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    private String updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private String remark;
}
