package org.dromara.book.domain.enums;

/**
 * 题型枚举 —— sys_dict_data 字典 {@code biz_question_type} 的后端代码侧镜像（SSOT 指针）。
 *
 * <p>🔴 唯一事实标准 = sys_dict_data 的 {@code biz_question_type}（超管可改）。本枚举是后端
 * 组卷 / 分组装 section 标题处用的镜像，消除 AutoPaper / QuestionService / 各 VO 里
 * 「各写一份题型名魔法值」——曾出现题型 5「简答题 vs 解答题」全平台口径漂移（字典=解答题）。
 * 字典改了须同步这里。
 *
 * <p>跨层同源镜像：题管线 = {@code teacher-mcp/app/dicts.py} {@code QUESTION_TYPE}；
 * 前端 = {@code book-ui/src/store/dict.ts} {@code DICT_QUESTION_TYPE}（走 useDictStore 直读字典）。
 */
public enum BizQuestionType {

    CHOICE(1, "选择题"),
    JUDGE(2, "判断题"),
    APPLICATION(3, "应用题"),
    FILL(4, "填空题"),
    ANSWER(5, "解答题"),
    DRAW(6, "作图题"),
    CALC(7, "计算题"),
    PROOF(8, "证明题");

    private final int code;
    private final String label;

    BizQuestionType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * code → 中文题型名；未知 / null code 回落 "题型{code}"（与前端 useDictStore.label 回落口径一致）。
     */
    public static String labelOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (BizQuestionType t : values()) {
            if (t.code == code) {
                return t.label;
            }
        }
        return "题型" + code;
    }
}
