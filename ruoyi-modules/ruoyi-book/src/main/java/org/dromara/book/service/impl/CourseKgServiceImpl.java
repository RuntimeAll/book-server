package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.CourseKgVo;
import org.dromara.book.mapper.CourseKgMapper;
import org.dromara.book.service.ICourseKgService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 从思维导图 content 里抠第一个 <img src="..."> */
    private static final Pattern IMG_SRC = Pattern.compile("<img[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

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

        // 思维导图 URL —— 从课时节点 content_type=思维导图 的 content 抠 img src
        vo.setMindmapUrl(extractImgSrc(courseKgMapper.selectMindmapContent(courseId)));

        // 内嵌例题（知识精讲）先查出来，按主知识点归到对应 kp
        List<Map<String, Object>> examples = courseKgMapper.selectQuestionsByColumn(BOOK_ID, courseId, "知识精讲");

        // 考点（level5） → 知识点（level6）
        for (Map<String, Object> kdRow : courseKgMapper.selectKaodians(courseId)) {
            CourseKgVo.Kaodian kd = new CourseKgVo.Kaodian();
            kd.setId(str(kdRow.get("id")));
            kd.setName(str(kdRow.get("name")));

            for (Map<String, Object> kpRow : courseKgMapper.selectKps(kd.getId())) {
                CourseKgVo.Kp kp = new CourseKgVo.Kp();
                String kpId = str(kpRow.get("id"));
                kp.setId(kpId);
                kp.setName(str(kpRow.get("name")));

                // blocks
                for (Map<String, Object> b : courseKgMapper.selectBlocks(kpId)) {
                    CourseKgVo.Block block = new CourseKgVo.Block();
                    block.setType(str(b.get("type")));
                    block.setContent(str(b.get("content")));
                    kp.getBlocks().add(block);
                }

                // keyConcepts
                kp.setKeyConcepts(courseKgMapper.selectKeyConcepts(kpId));

                // examples —— 主知识点 = 本 kp 的例题
                for (Map<String, Object> ex : examples) {
                    if (kpId.equals(str(ex.get("primaryKnowledgeId")))) {
                        kp.getExamples().add(toExercise(ex));
                    }
                }

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

    private static String extractImgSrc(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = IMG_SRC.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
