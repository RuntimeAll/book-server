package org.dromara.book.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 课时知识梳理讲义 VO（只读）—— 供前端画板/讲义页按结构化积木还原。
 *
 * <p>接口 GET /teacher/kg/course/{courseId}，命中 MisiktEnvelopeAdvice 包成
 * {code:1, message, response:CourseKgVo}。
 *
 * <p>🔴 v2 结构化数据模型（2026-07-01 重构）：内容不再是「一坨 HTML」，而是
 * biz_kg_block「一块一行」的结构化积木（para/note/callout/image/table/example），
 * 思维导图来自 biz_mindmap_node 节点树（前端建树·秒出），不再截图。
 *
 * <p>树形：course(level4) → kaodians(level5) → kps(level6)；kp 挂 blocks/keyConcepts；
 * exercises(习题精练) / boost(巩固提升) 仍平铺（来自 biz_book_question）。qid 一律 String
 * （biz_question.id 是 BIGINT，超出 JS 安全整数会丢精度，必须字符串传）。
 *
 * @author codeplace-C
 */
@Data
public class CourseKgVo {

    /** 课时节点 {id, name} */
    private Course course;

    /** 思维导图节点树（扁平列表，前端按 parentKey 建树·秒出）；来自 biz_mindmap_node */
    private List<MindmapNode> mindmap = new ArrayList<>();

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

    /** 思维导图节点（biz_mindmap_node 一行一节点） */
    @Data
    public static class MindmapNode {
        /** 节点键，如 n1 */
        private String nodeKey;
        /** 父节点键；根节点为 null */
        private String parentKey;
        /** 节点文字 */
        private String text;
        /** 展开细节（可空） */
        private String detail;
        /** detail 是否含记忆点标红 */
        private Integer hasMark;
        /** 节点色（可空，一级分支上色） */
        private String color;
        /** 同层排序 */
        private Integer sort;
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
        /** 知识点结构化积木块（para/note/callout/image/table/example，按 seq） */
        private List<Block> blocks = new ArrayList<>();
        /** 标红记忆点关键字（按 sort） */
        private List<String> keyConcepts = new ArrayList<>();
    }

    /** 知识点结构化积木块（biz_kg_block 一块一行） */
    @Data
    public static class Block {
        /** 显示顺序 */
        private Integer seq;
        /** 块类型：para | note | callout | image | table | example */
        private String type;
        /** 块负载（已解析成对象/数组，随 type 而异；结构见前端 renderer 约定） */
        private Object payload;
    }

    /** 题目（习题精练 / 巩固提升，来自 biz_book_question） */
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
