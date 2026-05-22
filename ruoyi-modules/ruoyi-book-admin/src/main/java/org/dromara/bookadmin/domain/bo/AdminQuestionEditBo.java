package org.dromara.bookadmin.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * /admin/question/edit 新建/编辑统一入参 BO（H1 卡段② BE 波 2b — V-1 + V-6）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本 BO 物理落 {@code ruoyi-book-admin.domain.bo}，
 * <strong>禁与 teacher 共享</strong>。字段映射严格对齐 PRD §3.1 契约。
 *
 * <p>字段语义（PRD §3.1 + §3.5 契约源）：
 * <ul>
 *   <li>{@code id} — null=新建 / 非空=编辑（service 层据此分支）</li>
 *   <li>{@code questionType} — 1选择 / 2填空 / 3判断 / 4计算 / 5解答</li>
 *   <li>{@code difficult} — 1..4（注意 DB 字段名是 difficult 不是 difficulty）</li>
 *   <li>{@code subjectId} — biz_subject.id（章节-知识点编码）</li>
 *   <li>{@code stemText} / {@code stemImgUrl} — 至少有一个非空（PRD §6 R7）</li>
 *   <li>{@code optionsJson} — 选择题 (type=1) 必填且 size ≥ 2；Jackson 序列化为 JSON 字符串落 DB JSON 列</li>
 *   <li>{@code correctAnswer} — 选择题必填且必须 ∈ optionsJson[*].key</li>
 *   <li>{@code scoreStdJson} — OOS-3 本卡不录入，默认 null（保留入参兼容性）</li>
 *   <li>{@code tagNames} — 字符串数组，BE 维护 biz_question_free_tag + biz_free_tag 字典 + 冗余串</li>
 *   <li>{@code questionKnowledges} — at least 1（U 轨）；item 含 {@code knowledgeId} + {@code source}（service 层强制 'U'）</li>
 * </ul>
 *
 * @author backend-dev (H1 卡段② BE 波 2b)
 */
@Data
public class AdminQuestionEditBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目 ID（null=新建 / 非空=编辑）
     */
    private Long id;

    /**
     * 题型：1=选择 / 2=填空 / 3=判断 / 4=计算 / 5=解答
     */
    private Integer questionType;

    /**
     * 难度 1..4 星
     */
    private Integer difficult;

    /**
     * 章节-知识点编码（biz_subject.id 任意层级）
     */
    private String subjectId;

    /**
     * 题目简短标题（可空）
     */
    private String shortTitle;

    /**
     * 题干文本（PRD §6 R7：与 stemImgUrl 至少有一个非空）
     */
    private String stemText;

    /**
     * 题干图 URL（PRD §6 R7：与 stemText 至少有一个非空）
     */
    private String stemImgUrl;

    /**
     * 答案图 URL（可空）
     */
    private String answerImgUrl;

    /**
     * 解析图 URL（可空）
     */
    private String explainImgUrl;

    /**
     * 选项 JSON（List of {key,content}）— questionType=1 选择题必填 ≥ 2 项。
     *
     * <p>Jackson 序列化为字符串后落 biz_question.options_json (MySQL JSON 列)。
     */
    private List<Map<String, Object>> optionsJson;

    /**
     * 正确答案（选择题为 A/B/C/D；其他题型为文本）
     */
    private String correctAnswer;

    /**
     * 评分标准 JSON（OOS-3 本卡不录入，默认 null）
     */
    private String scoreStdJson;

    /**
     * 自由标签数组（FE 传字符串数组）。
     *
     * <p>BE 维护 biz_question_free_tag 关联表 + biz_free_tag 字典（自动建）+
     * biz_question.free_tag 冗余串（{@code tagNames.join(",")}）— 全量替换语义（PRD §3.5）。
     */
    private List<String> tagNames;

    /**
     * 关联知识点（≥1）。
     *
     * <p>每项含 {@code knowledgeId} + 可选 {@code source}（service 层强制 'U' 写入，
     * 防 FE 误传 'S' 把标准库知识点污染）。
     */
    private List<QuestionKnowledgeItem> questionKnowledges;

    /**
     * 关联知识点 item（嵌套对象）。
     */
    @Data
    public static class QuestionKnowledgeItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * biz_subject.id（关联章节-知识点节点）
         */
        private String knowledgeId;

        /**
         * 来源：'U'=用户标注 / 'S'=标准库标注；service 层强制 'U' 写入（防污染）
         */
        private String source;
    }
}
