package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.CoursePlanBo;
import org.dromara.book.domain.bo.CoursePlanLessonBo;
import org.dromara.book.domain.bo.LessonBatchBo;
import org.dromara.book.domain.entity.BizClass;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizClassMapper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizCoursePlanMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程计划 Service（PRD-C-213 /teacher/schedule/plan）。
 *
 * <p>大纲/细备双精度；total_lessons=count(lessons) 实时聚合；深拷贝含课次；家长版两列长图导出。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class CoursePlanService {

    private final BizCoursePlanMapper planMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizStudentMapper studentMapper;
    private final BizClassMapper classMapper;
    private final ScheduleRenderUtil renderUtil;

    @Transactional(rollbackFor = Exception.class)
    public Long upsertPlan(CoursePlanBo bo) {
        BizCoursePlan p = bo.getId() == null ? new BizCoursePlan() : planMapper.selectById(bo.getId());
        if (p == null) throw new ServiceException("计划不存在");
        if (bo.getName() != null) p.setName(bo.getName());
        if (bo.getTargetType() != null) p.setTargetType(bo.getTargetType());
        p.setTermTag(bo.getTermTag());
        p.setYear(bo.getYear());
        p.setMaterialNote(bo.getMaterialNote());
        if (bo.getDefaultSegTemplate() != null) {
            p.setDefaultSegTemplate(JsonUtils.toJsonString(bo.getDefaultSegTemplate()));
        }
        if (bo.getStatus() != null) p.setStatus(bo.getStatus());
        if (bo.getId() == null) {
            if (p.getStatus() == null) p.setStatus("0");
            if (p.getTargetType() == null) p.setTargetType("0");
            planMapper.insert(p);
        } else {
            planMapper.updateById(p);
        }
        return p.getId();
    }

    public List<Map<String, Object>> page(String targetType, String keyword) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizCoursePlan> w = new LambdaQueryWrapper<BizCoursePlan>()
            .eq(BizCoursePlan::getCreateBy, uid)
            .eq(targetType != null && !targetType.isBlank(), BizCoursePlan::getTargetType, targetType)
            .like(keyword != null && !keyword.isBlank(), BizCoursePlan::getName, keyword)
            .orderByDesc(BizCoursePlan::getId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizCoursePlan p : planMapper.selectList(w)) {
            Map<String, Object> m = planBrief(p);
            long total = lessonMapper.selectCount(
                new LambdaQueryWrapper<BizCoursePlanLesson>().eq(BizCoursePlanLesson::getPlanId, p.getId()));
            m.put("totalLessons", total);
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> planBrief(BizCoursePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("name", p.getName());
        m.put("targetType", p.getTargetType());
        m.put("termTag", p.getTermTag());
        m.put("year", p.getYear());
        m.put("materialNote", p.getMaterialNote());
        m.put("defaultSegTemplate", parse(p.getDefaultSegTemplate()));
        m.put("status", p.getStatus());
        return m;
    }

    /** 详情含 lessons 全量。 */
    public Map<String, Object> detail(Long id) {
        BizCoursePlan p = planMapper.selectById(id);
        if (p == null) throw new ServiceException("计划不存在");
        Map<String, Object> m = planBrief(p);
        List<BizCoursePlanLesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<BizCoursePlanLesson>()
            .eq(BizCoursePlanLesson::getPlanId, id).orderByAsc(BizCoursePlanLesson::getLessonSeq));
        List<Map<String, Object>> ls = new ArrayList<>();
        for (BizCoursePlanLesson l : lessons) ls.add(lessonVo(l));
        m.put("lessons", ls);
        m.put("totalLessons", ls.size());
        return m;
    }

    public Map<String, Object> lessonVo(BizCoursePlanLesson l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(l.getId()));
        m.put("planId", String.valueOf(l.getPlanId()));
        m.put("lessonSeq", l.getLessonSeq());
        m.put("title", l.getTitle());
        m.put("lessonType", l.getLessonType());
        m.put("tag", l.getTag());
        m.put("sourceRef", l.getSourceRef());
        m.put("thinkingAction", l.getThinkingAction());
        m.put("layerTarget", l.getLayerTarget());
        m.put("parentCopy", l.getParentCopy());
        m.put("kgNodeIds", parse(l.getKgNodeIds()));
        m.put("segTemplate", parse(l.getSegTemplate()));
        m.put("prepState", l.getPrepState());
        return m;
    }

    /** 批量 upsert 课次（id 空=新增）。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> upsertLessons(Long planId, LessonBatchBo bo) {
        if (planMapper.selectById(planId) == null) throw new ServiceException("计划不存在");
        List<Long> ids = new ArrayList<>();
        if (bo.getLessons() == null) return ids;
        for (CoursePlanLessonBo lb : bo.getLessons()) {
            BizCoursePlanLesson l = lb.getId() == null ? new BizCoursePlanLesson() : lessonMapper.selectById(lb.getId());
            if (l == null) l = new BizCoursePlanLesson();
            l.setPlanId(planId);
            if (lb.getLessonSeq() != null) l.setLessonSeq(lb.getLessonSeq());
            l.setTitle(lb.getTitle());
            l.setLessonType(lb.getLessonType() == null ? "0" : lb.getLessonType());
            l.setTag(lb.getTag());
            l.setSourceRef(lb.getSourceRef());
            l.setThinkingAction(lb.getThinkingAction());
            l.setLayerTarget(lb.getLayerTarget());
            l.setParentCopy(lb.getParentCopy());
            if (lb.getKgNodeIds() != null) l.setKgNodeIds(JsonUtils.toJsonString(lb.getKgNodeIds()));
            if (lb.getSegTemplate() != null) l.setSegTemplate(JsonUtils.toJsonString(lb.getSegTemplate()));
            if (lb.getPrepState() != null) l.setPrepState(lb.getPrepState());
            if (lb.getId() == null) {
                if (l.getPrepState() == null) l.setPrepState("0");
                if (l.getLessonSeq() == null) l.setLessonSeq(0);
                lessonMapper.insert(l);
            } else {
                lessonMapper.updateById(l);
            }
            ids.add(l.getId());
        }
        return ids;
    }

    public void deleteLesson(Long lessonId) {
        lessonMapper.deleteById(lessonId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderLessons(Long planId, List<Long> lessonIds) {
        if (lessonIds == null) return;
        int seq = 1;
        for (Long lid : lessonIds) {
            BizCoursePlanLesson l = lessonMapper.selectById(lid);
            if (l != null && planId.equals(l.getPlanId())) {
                l.setLessonSeq(seq);
                lessonMapper.updateById(l);
            }
            seq++;
        }
    }

    /** 深拷贝含课次。 */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long planId) {
        BizCoursePlan src = planMapper.selectById(planId);
        if (src == null) throw new ServiceException("计划不存在");
        BizCoursePlan p = new BizCoursePlan();
        p.setName(src.getName() + "（副本）");
        p.setTargetType(src.getTargetType());
        p.setTermTag(src.getTermTag());
        p.setYear(src.getYear());
        p.setMaterialNote(src.getMaterialNote());
        p.setDefaultSegTemplate(src.getDefaultSegTemplate());
        p.setStatus("0");
        planMapper.insert(p);
        List<BizCoursePlanLesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<BizCoursePlanLesson>()
            .eq(BizCoursePlanLesson::getPlanId, planId).orderByAsc(BizCoursePlanLesson::getLessonSeq));
        for (BizCoursePlanLesson s : lessons) {
            BizCoursePlanLesson l = new BizCoursePlanLesson();
            l.setPlanId(p.getId());
            l.setLessonSeq(s.getLessonSeq());
            l.setTitle(s.getTitle());
            l.setLessonType(s.getLessonType());
            l.setTag(s.getTag());
            l.setSourceRef(s.getSourceRef());
            l.setThinkingAction(s.getThinkingAction());
            l.setLayerTarget(s.getLayerTarget());
            l.setParentCopy(s.getParentCopy());
            l.setKgNodeIds(s.getKgNodeIds());
            l.setSegTemplate(s.getSegTemplate());
            l.setPrepState("0");
            lessonMapper.insert(l);
        }
        return p.getId();
    }

    /**
     * 家长版两列长图导出（对照 06 样张）。🔴 内部字段一律不出现：
     * 素材源/思维动作/层数/星级/prep态/题数/肖像/吃透课编号。
     */
    public Map<String, Object> parentExport(Long planId, Long targetId) {
        BizCoursePlan p = planMapper.selectById(planId);
        if (p == null) throw new ServiceException("计划不存在");
        List<BizCoursePlanLesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<BizCoursePlanLesson>()
            .eq(BizCoursePlanLesson::getPlanId, planId).orderByAsc(BizCoursePlanLesson::getLessonSeq));
        if (lessons.isEmpty()) throw new ServiceException("计划无课次，无法导出家长课表");

        // 对象名
        String targetName = p.getName();
        String subject = null;
        if (targetId != null) {
            if ("1".equals(p.getTargetType())) {
                BizClass c = classMapper.selectById(targetId);
                if (c != null) { targetName = c.getName(); subject = c.getSubject(); }
            } else {
                BizStudent s = studentMapper.selectById(targetId);
                if (s != null) { targetName = s.getName(); subject = s.getSubject(); }
            }
        }

        // lesson_id -> 已排 session 日期时间（取该对象该计划的场次）
        Map<Long, BizScheduleSession> lessonToSession = new LinkedHashMap<>();
        if (targetId != null) {
            List<BizScheduleSession> ss = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getTargetType, p.getTargetType())
                .eq(BizScheduleSession::getTargetId, targetId)
                .eq(BizScheduleSession::getPlanId, planId));
            for (BizScheduleSession s : ss) {
                if (s.getPlanLessonId() != null) lessonToSession.putIfAbsent(s.getPlanLessonId(), s);
            }
        }

        String html = renderUtil.buildParentHtml(targetName, subject, p, lessons, lessonToSession);
        int rowsPerCol = (lessons.size() + 1) / 2;
        int height = 140 + rowsPerCol * 70;
        String file = renderUtil.screenshot(html, "parent_" + planId, 900, height);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("url", "/teacher/schedule/artifact?path=" + file);
        return m;
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonUtils.parseObject(json, Object.class);
    }
}
