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
 *   <li>{@code difficult} 不是 difficulty（1-4 星）</li>
 *   <li>{@code createTime} BIGINT ms timestamp（归一化）</li>
 *   <li>{@code questionKnowledges} 列表 + 详情都返（source='U'）</li>
 * </ul>
 *
 * <p>page 不返：{@code answer / explain / fileBin / questionStdKnowledges}（详情专属字段）。
 * 但因为 mapper.xml 用同一个 ResultMap 简化复用 — 详情字段在 page SQL 里不 SELECT，序列化时为 null 由 FE 容忍。
 *
 * <p>🔴 PRD-B-013 减法：删除 5 个 misikt 复刻期残留字段（详见 PRD-B-013 §scope.B 后端段）。
 *
 * @author backend-dev
 */
@Data
public class QuestionItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 题型 1=选择 / 4=填空 / 5=解答
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
     * 题干文本
     */
    private String stemText;

    /**
     * 题干图 URL（misikt JSON 字段 stemImg）
     */
    private String stemImg;

    /**
     * 自由标签（原 biz_question.free_tag 串字段）。
     *
     * <p>🔴 V905 schema 收敛：free_tag 列已 DROP，本字段不再回填（恒 null），
     * 真相走结构化 {@link #freeTags}（biz_question_free_tag 挂接表）。
     * 字段保留仅为向下兼容 FE 序列化契约，待 FE 切净后随 PRD 一并删。
     */
    private String freeTag;

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

    /**
     * 题干富文本（biz_text_content.content）。
     *
     * <p>🔴 按 FK 关联：biz_question.stem_text_content_id = biz_text_content.id —— 不是按
     * question_id+content_type（一题在 biz_text_content 可能有多条历史记录，FK 指定启用哪条）。
     * FK 为 NULL 时返 null。与老字段 {@link #stemText}（来源闸 resolved）并存，不替换。
     */
    private String stemTextContent;

    /**
     * 答案富文本（biz_text_content.content，FK = biz_question.answer_text_content_id）。FK 为 NULL 返 null。
     */
    private String answerTextContent;

    /**
     * 解析富文本（biz_text_content.content，FK = biz_question.analyze_text_content_id）。FK 为 NULL 返 null。
     */
    private String analyzeTextContent;

    /**
     * PRD-A-015 结构化网格块 JSON（biz_question_block.block_json，按 question_id 关联）。
     *
     * <p>列表卡片（我的题库/题库/工作台中栏/卷篮）有则走 QuestionBlockRender 结构化渲染，
     * 与详情/卷库/PDF 四端一致；null=未结构化老题，FE 回落 stemText/stemImg 扁平展示。
     * page() 批量回填（仿 selectById/listByIds），不再「列表端空返」。
     */
    private String blockJson;

    /**
     * PRD-C-204 答案 blockJson（统一富文本格式化层；走 convertRichText）。详情页有则走
     * QuestionBlockRender 结构化渲染，null=回落旧富文本（answerTextContent/answer）。
     */
    private String answerBlockJson;

    /**
     * PRD-C-204 解析 blockJson（选项分析/小问/步骤拆块，三端统一渲染）。null=回落旧富文本。
     */
    private String analyzeBlockJson;

    /**
     * AI 打标状态（biz_question.label_status）：0=未标 / 1=AI已标 / 2=已审核。
     */
    private Integer labelStatus;

    /**
     * 题型徽标（PRD-C-204）：该题挂接的题型数组 [{patternId,name,isPrimary}]。
     *
     * <p>来源 biz_question_pattern_rel JOIN biz_question_pattern，page/list/select
     * 二次批量回填（仿 freeTags/blockJson，非 N+1）。没题型的题返空数组。
     */
    private List<PatternRefVo> patterns;
}
