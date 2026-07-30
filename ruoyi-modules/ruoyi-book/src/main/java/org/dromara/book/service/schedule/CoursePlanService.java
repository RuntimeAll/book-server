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
import org.dromara.book.util.EduTermUtil;
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
    private final PaperSlotService paperSlotService;
    /** PRD-015：学科归属校验（学生×学科账户 = 学科绑定的物理载体，D3）。 */
    private final TuitionAccountService accountService;

    @Transactional(rollbackFor = Exception.class)
    public Long upsertPlan(CoursePlanBo bo) {
        BizCoursePlan p = bo.getId() == null ? new BizCoursePlan() : planMapper.selectById(bo.getId());
        if (p == null) throw new ServiceException("计划不存在");
        // PRD-015 学科归属校验用：记录改动前的学科（不变则不重复校验，老计划照旧可编辑）
        String oldSubject = p.getSubject();
        if (bo.getName() != null) p.setName(bo.getName());
        if (bo.getTargetType() != null) p.setTargetType(bo.getTargetType());
        if (bo.getTargetId() != null) p.setTargetId(bo.getTargetId());
        p.setTermTag(bo.getTermTag());
        if (bo.getSubject() != null) p.setSubject(EduTermUtil.normalizeSubject(bo.getSubject()));
        p.setYear(bo.getYear());
        p.setMaterialNote(bo.getMaterialNote());
        if (bo.getDefaultSegTemplate() != null) {
            p.setDefaultSegTemplate(JsonUtils.toJsonString(bo.getDefaultSegTemplate()));
        }
        if (bo.getDefaultPaperSlots() != null) {
            List<Map<String, Object>> slots = paperSlotService.parseSlots(JsonUtils.toJsonString(bo.getDefaultPaperSlots()));
            paperSlotService.validateSlots(slots);
            p.setDefaultPaperSlots(JsonUtils.toJsonString(slots));
        }
        if (bo.getStatus() != null) p.setStatus(bo.getStatus());
        if (bo.getId() == null) {
            if (p.getStatus() == null) p.setStatus("0");
            if (p.getTargetType() == null) p.setTargetType("0");
            // S1：建计划必传归属对象，且对象必须存在
            if (p.getTargetId() == null) {
                throw new ServiceException("建计划必须指定归属对象 targetId（S1）", 400);
            }
            requireTarget(p.getTargetType(), p.getTargetId());
            requireSubjectAccount(p, oldSubject);
            planMapper.insert(p);
        } else {
            requireSubjectAccount(p, oldSubject);
            planMapper.updateById(p);
        }
        return p.getId();
    }

    /**
     * PRD-015 学科归属校验（D3「开户即绑定学科」）：学生对象的计划，其学科必须已开通课时账户。
     *
     * <p>只在<b>学科真的变了</b>（建计划 / 改学科）时校验——不变则放行，存量老计划照旧可编辑。
     * 🔴 班级（target_type='1'）跳过：班课收费模型未拍，账户仅学生对象（PRD-015 §9 边界）。
     */
    private void requireSubjectAccount(BizCoursePlan p, String oldSubject) {
        if ("1".equals(p.getTargetType())) return;
        String subject = p.getSubject();
        if (subject == null || subject.isBlank()) return;
        if (subject.equals(oldSubject)) return;
        if (!accountService.hasAccount(p.getTargetId(), subject)) {
            throw new ServiceException("先为学生开通" + EduTermUtil.subjectLabel(subject) + "账户", 400);
        }
    }

    /** S1：归属对象存在性校验。 */
    private void requireTarget(String targetType, Long targetId) {
        boolean exists = "1".equals(targetType)
            ? classMapper.selectById(targetId) != null
            : studentMapper.selectById(targetId) != null;
        if (!exists) {
            throw new ServiceException("计划归属对象不存在：targetType=" + targetType + ", targetId=" + targetId, 400);
        }
    }

    /** 计划列表（FE PageResult&lt;PlanVO&gt; = {rows,total}）。S1：支持按归属对象 targetId 过滤。 */
    public Map<String, Object> page(String targetType, String keyword, Long targetId) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizCoursePlan> w = new LambdaQueryWrapper<BizCoursePlan>()
            .eq(BizCoursePlan::getCreateBy, uid)
            .eq(targetType != null && !targetType.isBlank(), BizCoursePlan::getTargetType, targetType)
            .eq(targetId != null, BizCoursePlan::getTargetId, targetId)
            .like(keyword != null && !keyword.isBlank(), BizCoursePlan::getName, keyword)
            .orderByDesc(BizCoursePlan::getId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizCoursePlan p : planMapper.selectList(w)) {
            Map<String, Object> m = planBrief(p);
            long total = lessonMapper.selectCount(
                new LambdaQueryWrapper<BizCoursePlanLesson>().eq(BizCoursePlanLesson::getPlanId, p.getId()));
            m.put("lessonCount", total);
            out.add(m);
        }
        return ScheduleSessionService.pageResult(out);
    }

    private Map<String, Object> planBrief(BizCoursePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(p.getId()));
        m.put("name", p.getName());
        m.put("targetType", p.getTargetType());
        m.put("targetId", p.getTargetId() == null ? null : String.valueOf(p.getTargetId()));
        m.put("termTag", p.getTermTag());
        m.put("subject", p.getSubject());
        m.put("subjectLabel", EduTermUtil.subjectLabel(p.getSubject()));
        m.put("year", p.getYear());
        m.put("materialNote", p.getMaterialNote());
        m.put("defaultSegTemplate", parse(p.getDefaultSegTemplate()));
        m.put("defaultPaperSlots", parse(p.getDefaultPaperSlots()));
        m.put("status", p.getStatus());
        m.put("createTime", p.getCreateTime());
        m.put("updateTime", p.getUpdateTime());
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
        for (BizCoursePlanLesson l : lessons) ls.add(lessonVo(l, p.getDefaultPaperSlots()));
        m.put("lessons", ls);
        m.put("lessonCount", ls.size());
        return m;
    }

    /**
     * 课次 VO（PRD-B-101）。备课态唯一权威 = lesson.paper_slots 实时推导（不再读 biz_prep_pack.status）。
     * paperSlots 输出「有效卷位」= 课次自有卷位；课次未配则回退计划默认模板（继承语义，读时回退不物理复制）。
     * prepState 键名保留兼容 FE。
     *
     * @param planDefaultPaperSlots 计划级默认卷位模板 JSON（课次无卷位时继承）
     */
    public Map<String, Object> lessonVo(BizCoursePlanLesson l, String planDefaultPaperSlots) {
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
        // 有效卷位：课次自有为准，未配则读时回退计划默认（不物理复制→改模板不回写已覆盖课次）
        boolean ownSlots = l.getPaperSlots() != null && !l.getPaperSlots().isBlank();
        String effectiveJson = ownSlots ? l.getPaperSlots() : planDefaultPaperSlots;
        List<Map<String, Object>> effectiveSlots = paperSlotService.parseSlots(effectiveJson);
        m.put("paperSlots", effectiveSlots);
        m.put("paperSlotsInherited", !ownSlots);
        // 备课态推导（PRD-003 D7）：改按材料位 special_ids ∪ book_node_ids（有专项或有书章节=已备好），
        // 与首页提醒/日程/月历同源，杜绝双权威分裂。paperSlots 字段仍输出（只读兼容期，B-101 旧数据留库不展示）。
        m.put("prepState", paperSlotService.deriveStatusFromMaterials(l.getSpecialIds(), l.getBookNodeIds()));
        return m;
    }

    /**
     * 批量 upsert 课次（id 空=新增），返回 PlanLessonVO[]（对齐 FE upsertLessons 返回类型）。
     * body 兼容两形：直接数组 PlanLessonBo[]（FE），或 {lessons:[...]}（MCP 旧格式）。
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> upsertLessons(Long planId, Object body) {
        BizCoursePlan plan = planMapper.selectById(planId);
        if (plan == null) throw new ServiceException("计划不存在");
        // 归一化到 List<CoursePlanLessonBo>
        Object listNode = body;
        if (body instanceof Map<?, ?> mp && mp.get("lessons") != null) {
            listNode = mp.get("lessons");
        }
        List<CoursePlanLessonBo> lessons = JsonUtils.getObjectMapper().convertValue(
            listNode, new com.fasterxml.jackson.core.type.TypeReference<List<CoursePlanLessonBo>>() {
            });
        List<Map<String, Object>> out = new ArrayList<>();
        if (lessons == null) return out;
        List<BizCoursePlanLesson> saved = new ArrayList<>();
        for (CoursePlanLessonBo lb : lessons) {
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
            if (lb.getPaperSlots() != null) {
                String oldSlotsJson = l.getPaperSlots();
                List<Map<String, Object>> slots = paperSlotService.parseSlots(JsonUtils.toJsonString(lb.getPaperSlots()));
                paperSlotService.validateSlots(slots);
                // M-1（PRD-B-101 §10 反性）：整体覆写时若任一原绑定消失（卷位被删 / paper_id 被清或换）
                // → 该课次全部卷位 manual_ready 清 false（与 unbindSlot 单点解绑口径一致，杜绝"删掉绑卷卷位仍显手动绿"）。
                // 纯新增 / 改名 / 改配方不触发。
                if (paperSlotService.anyBindingLost(oldSlotsJson, slots)) {
                    for (Map<String, Object> s : slots) s.put("manual_ready", false);
                }
                l.setPaperSlots(JsonUtils.toJsonString(slots));
            }
            if (lb.getId() == null) {
                if (l.getLessonSeq() == null) l.setLessonSeq(0);
                lessonMapper.insert(l);
            } else {
                lessonMapper.updateById(l);
            }
            saved.add(l);
        }
        for (BizCoursePlanLesson l : saved) out.add(lessonVo(l, plan.getDefaultPaperSlots()));
        return out;
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
        p.setTargetId(src.getTargetId());
        p.setTermTag(src.getTermTag());
        p.setYear(src.getYear());
        p.setMaterialNote(src.getMaterialNote());
        p.setDefaultSegTemplate(src.getDefaultSegTemplate());
        p.setDefaultPaperSlots(src.getDefaultPaperSlots());
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
            // PRD-B-101 拍板：副本卷位配方（slot_seq/name/style/rules/note）照抄，
            // 但 paper_id 置 null + manual_ready 置 false——备课卷是特定学生/日期的产物，
            // 副本课次从"未备"起步（plan 级 default_paper_slots 模板照抄不动，本就不含绑定）。
            l.setPaperSlots(stripSlotBindings(s.getPaperSlots()));
            lessonMapper.insert(l);
        }
        return p.getId();
    }

    /** 卷位深拷贝清绑定：paper_id→null、manual_ready→false，配方字段原样保留。空/无卷位原样返回。 */
    private String stripSlotBindings(String paperSlotsJson) {
        if (paperSlotsJson == null || paperSlotsJson.isBlank()) return paperSlotsJson;
        List<Map<String, Object>> slots = paperSlotService.parseSlots(paperSlotsJson);
        if (slots.isEmpty()) return paperSlotsJson;
        for (Map<String, Object> s : slots) {
            s.put("paper_id", null);
            s.put("manual_ready", false);
        }
        return JsonUtils.toJsonString(slots);
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

        // 对象名（R1a：subject 列存字典码，家长版长图要人话 → EduTermUtil 推导标签）
        String targetName = p.getName();
        String subject = null;
        if (targetId != null) {
            if ("1".equals(p.getTargetType())) {
                BizClass c = classMapper.selectById(targetId);
                if (c != null) { targetName = c.getName(); subject = EduTermUtil.subjectLabel(c.getSubject()); }
            } else {
                BizStudent s = studentMapper.selectById(targetId);
                if (s != null) { targetName = s.getName(); subject = EduTermUtil.subjectLabel(s.getSubject()); }
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
        // BUG-010 去浏览器：HTML → 进程内 PDF → pdfbox 光栅化 PNG（192dpi，布局 900px 不变、位图 2 倍保清晰）
        String file = renderUtil.renderToPng(html, "parent_" + planId, 900, height);
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
