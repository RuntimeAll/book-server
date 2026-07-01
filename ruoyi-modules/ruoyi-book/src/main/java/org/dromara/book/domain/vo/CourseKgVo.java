package org.dromara.book.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 课时知识梳理讲义 VO（只读）—— 供前端 1:1 还原教辅讲义页。
 *
 * <p>接口 GET /teacher/kg/course/{courseId}，命中 MisiktEnvelopeAdvice 包成
 * {code:1, message, response:CourseKgVo}。
 *
 * <p>树形：course(level4) → kaodians(level5) → kps(level6)；kp 挂 blocks/keyConcepts/examples；
 * exercises(习题精练) / boost(巩固提升) 平铺。qid 一律 String（biz_question.id 是 BIGINT，
 * 超出 JS 安全整数会丢精度，必须字符串传）。
 *
 * @author backend-dev
 */
@Data
public class CourseKgVo {

    /** 课时节点 {id, name} */
    private Course course;

    /** 课时节点上 content_type=思维导图 那条里的 <img src>；无则 null */
    private String mindmapUrl;

    /** 考点（level5）列表 */
    private List<Kaodian> kaodians = new ArrayList<>();

    /** 习题精练（平铺，按 in_block_seq） */
    private List<Exercise> exercises = new ArrayList<>();

    /** 巩固提升（平铺，按 in_block_seq） */
    private List<Exercise> boost = new ArrayList<>();

    @Data
    public static class Course {
        private String id;
        private String name;
    }

    /** 考点（level5） */
    @Data
    public static class Kaodian {
        private String id;
        private String name;
        private List<Kp> kps = new ArrayList<>();
    }

    /** 知识点（level6） */
    @Data
    public static class Kp {
        private String id;
        private String name;
        /** 知识点正文块（讲解/名师解读/表格，按 sort） */
        private List<Block> blocks = new ArrayList<>();
        /** 标红记忆点关键字（按 sort） */
        private List<String> keyConcepts = new ArrayList<>();
        /** 内嵌例题（知识精讲，按 in_block_seq），归到本知识点 */
        private List<Exercise> examples = new ArrayList<>();
    }

    /** 知识点正文块 */
    @Data
    public static class Block {
        /** 讲解 | 名师解读 | 表格 */
        private String type;
        /** 富文本 HTML */
        private String content;
    }

    /** 题目（例题 / 习题 / 巩固） */
    @Data
    public static class Exercise {
        /** biz_question.id（String 防精度丢失） */
        private String qid;
        /** biz_question.question_type */
        private Integer type;
        /** biz_question.source_raw */
        private String source;
        /** biz_text_content content_type=S */
        private String stem;
        /** biz_text_content content_type=A */
        private String answer;
        /** biz_text_content content_type=E */
        private String explain;
    }
}
