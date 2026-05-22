package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * /teacher/question/page list 元素 VO（题目列表项）。
 *
 * <p>字段命名严格对齐 misikt 抓包（A3-question-page.json）+ 子 PRD §4.5 归一化口径：
 * <ul>
 *   <li>{@code stemImg} 不带 _url 后缀（DB 列是 stem_img_url）</li>
 *   <li>{@code answerImg / explainImg / fileBin} 同上</li>
 *   <li>{@code videoUrl} 保留 Url 后缀（misikt 自己不一致）</li>
 *   <li>{@code difficult} 不是 difficulty（1-4 星）</li>
 *   <li>{@code isShare} INT 0/1（归一化）</li>
 *   <li>{@code createTime} BIGINT ms timestamp（归一化）</li>
 *   <li>{@code questionKnowledges} 列表 + 详情都返（source='U'）</li>
 * </ul>
 *
 * <p>page 不返：{@code answer / explain / fileBin / videoUrl / questionStdKnowledges}（详情专属字段）。
 * 但因为 mapper.xml 用同一个 ResultMap 简化复用 — 详情字段在 page SQL 里不 SELECT，序列化时为 null 由 FE 容忍。
 *
 * @author backend-dev
 */
@Data
public class QuestionItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 题型 1=选择 / 4=填空 / 5=简答
     */
    private Integer questionType;

    /**
     * 难度 1-4 星
     */
    private Integer difficult;

    /**
     * 章节-知识点编码
     */
    private String subjectId;

    /**
     * 题目简短标题
     */
    private String shortTitle;

    /**
     * 题干文本
     */
    private String stemText;

    /**
     * 题干图 URL（misikt JSON 字段 stemImg）
     */
    private String stemImg;

    /**
     * 自由标签
     */
    private String freeTag;

    /**
     * 正确答案（选择题字母 / 文本）
     */
    private String correctAnswer;

    /**
     * 分值（page 阶段恒 0，真分值在 biz_paper_question）
     */
    private Integer score;

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
     * 是否共享 0/1
     */
    private Integer isShare;

    /**
     * 是否重复 0/1
     */
    private Integer isRepeat;

    /**
     * 重复源题 ID
     */
    private Long repeatQuestionId;

    /**
     * 状态 '0' / '1' / '2'（INT 化也兼容 — 这里走原 CHAR 透传）
     */
    private String status;

    /**
     * 创建用户 ID
     */
    private Long createUser;

    /**
     * 创建用户名（字符串型，跟 RuoYi create_by 字段一致）
     */
    private String createBy;

    /**
     * 创建时间（ms timestamp 归一化）
     */
    private Long createTime;

    /**
     * 更新时间（ms timestamp 归一化）
     */
    private Long updateTime;

    /**
     * 更新用户名
     */
    private String updateBy;

    /**
     * 用户标注知识点（source='U'，列表 + 详情都返）
     */
    private List<QuestionKnowledgeVo> questionKnowledges;

    /**
     * 结构化 freeTag 数组（X 卡段② / freeTag 字典化）。
     *
     * <p>跟 {@link #freeTag}（原始字符串字段）并存：
     * <ul>
     *   <li>老字段 {@code freeTag} = biz_question.free_tag 原始串（向下兼容，不删）</li>
     *   <li>新字段 {@code freeTags} = biz_question_free_tag JOIN biz_free_tag
     *       的结构化数组，元素 {id, name, position}，position 决定 FE 颜色</li>
     * </ul>
     *
     * <p>page / select / queryBasket / paper source 4 个端点都返。
     */
    private List<FreeTagVo> freeTags;

    /**
     * 当前用户是否已收藏该题（J 卡段② / LEFT JOIN biz_question_favorite by user_id）。
     *
     * <p>Boolean 而非 boolean — 区分"未登录/未查"(null) 与"未收藏"(false)。
     * page 端点固定回填（默认 false），FE 直接渲染心形态，免 N+1 GET /qd/favorite/{id}。
     */
    private Boolean isFavorite;
}
