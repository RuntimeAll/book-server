package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.CourseKgVo;
import org.dromara.book.mapper.CourseKgMapper;
import org.dromara.book.service.ICourseKgService;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 课时知识梳理讲义 Service 实现（只读）。
 *
 * <p>组装策略：一课时数据量小（十几个知识点、几十道题），直接逐节点小查询聚合，无并发要求。
 * 所有查询走主数据源（ai_lesson_prep），不写库。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class CourseKgServiceImpl implements ICourseKgService {

    /** 教辅书 id（本课时演示卷） */
    private static final String BOOK_ID = "CC7S";

    private final CourseKgMapper courseKgMapper;

    @Override
    public CourseKgVo getCourseKg(String courseId) {
        Map<String, Object> courseRow = courseKgMapper.selectCourse(courseId);
        if (courseRow == null || courseRow.isEmpty()) {
            return null;
        }

        CourseKgVo vo = new CourseKgVo();

        CourseKgVo.Course course = new CourseKgVo.Course();
        course.setId(str(courseRow.get("id")));
        course.setName(str(courseRow.get("name")));
        vo.setCourse(course);

        // 思维导图节点树（biz_mindmap_node）—— 前端按 parentKey 建树·秒出
        for (Map<String, Object> n : courseKgMapper.selectMindmap(courseId)) {
            CourseKgVo.MindmapNode node = new CourseKgVo.MindmapNode();
            node.setNodeKey(str(n.get("nodeKey")));
            node.setParentKey(str(n.get("parentKey")));
            node.setText(str(n.get("text")));
            node.setDetail(str(n.get("detail")));
            node.setHasMark(intOf(n.get("hasMark")));
            node.setColor(str(n.get("color")));
            node.setSort(intOf(n.get("sort")));
            vo.getMindmap().add(node);
        }

        // 考点（level5） → 知识点（level6） → 结构化积木块（biz_kg_block）
        for (Map<String, Object> kdRow : courseKgMapper.selectKaodians(courseId)) {
            CourseKgVo.Kaodian kd = new CourseKgVo.Kaodian();
            kd.setId(str(kdRow.get("id")));
            kd.setName(str(kdRow.get("name")));

            for (Map<String, Object> kpRow : courseKgMapper.selectKps(kd.getId())) {
                CourseKgVo.Kp kp = new CourseKgVo.Kp();
                String kpId = str(kpRow.get("id"));
                kp.setId(kpId);
                kp.setName(str(kpRow.get("name")));

                // blocks —— payload 存 JSON 字符串, 这里解析成对象透传给前端
                for (Map<String, Object> b : courseKgMapper.selectBlocks(kpId)) {
                    CourseKgVo.Block block = new CourseKgVo.Block();
                    block.setSeq(intOf(b.get("seq")));
                    block.setType(str(b.get("type")));
                    block.setPayload(parsePayload(str(b.get("payload"))));
                    kp.getBlocks().add(block);
                }

                // keyConcepts
                kp.setKeyConcepts(courseKgMapper.selectKeyConcepts(kpId));

                kd.getKps().add(kp);
            }
            vo.getKaodians().add(kd);
        }

        // 习题精练 / 巩固提升 平铺
        for (Map<String, Object> row : courseKgMapper.selectQuestionsByColumn(BOOK_ID, courseId, "习题精练")) {
            vo.getExercises().add(toExercise(row));
        }
        for (Map<String, Object> row : courseKgMapper.selectQuestionsByColumn(BOOK_ID, courseId, "巩固提升")) {
            vo.getBoost().add(toExercise(row));
        }

        return vo;
    }

    private CourseKgVo.Exercise toExercise(Map<String, Object> row) {
        CourseKgVo.Exercise e = new CourseKgVo.Exercise();
        e.setQid(str(row.get("qid")));
        Object type = row.get("type");
        e.setType(type == null ? null : ((Number) type).intValue());
        e.setSource(str(row.get("source")));
        e.setStem(str(row.get("stem")));
        e.setAnswer(str(row.get("answer")));
        e.setExplain(str(row.get("explainText")));
        return e;
    }

    /** payload JSON 字符串解析成对象（Map/List）透传前端；空/非法则原样字符串兜底。 */
    private static Object parsePayload(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        Object parsed = JsonUtils.parseObject(payload, Object.class);
        return parsed != null ? parsed : payload;
    }

    private static Integer intOf(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
