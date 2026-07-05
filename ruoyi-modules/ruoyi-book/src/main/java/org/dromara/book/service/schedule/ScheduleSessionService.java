package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ConflictCheckBo;
import org.dromara.book.domain.bo.SessionBatchBo;
import org.dromara.book.domain.bo.SessionItemBo;
import org.dromara.book.domain.bo.SessionUpdateBo;
import org.dromara.book.domain.entity.BizClass;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizClassMapper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 排课 Service（PRD-C-213 /teacher/schedule/session + calendar + stat + prep/todo）。
 *
 * <p>batch+autoBind、conflict-check（老师/学生撞场）、leave/cancel 顺延（契约§五-2，locked 跳过、
 * overflow 提示）、改期不触发顺延、mark-done/lock/unlock、14 天细备窗口。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class ScheduleSessionService {

    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizStudentMapper studentMapper;
    private final BizClassMapper classMapper;
    private final BizQuestionMapper questionMapper;

    // ─────────────────────── 冲突检测 ───────────────────────

    /** 预检冲突。 */
    public Map<String, Object> conflictCheck(String targetType, Long targetId, List<SessionItemBo> items) {
        List<Map<String, Object>> conflicts = detectConflicts(targetType, targetId, items, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conflicts", conflicts);
        return r;
    }

    /**
     * 老师撞场 = create_by 同人任意对象时间重叠（含外部占位）；学生撞场 = 同 target 重叠。
     * 请假/取消场次（status 2/3）不算冲突源。excludeId 用于改期时排除自身。
     */
    private List<Map<String, Object>> detectConflicts(String targetType, Long targetId,
                                                       List<SessionItemBo> items, Long excludeId) {
        Long uid = LoginHelper.getUserId();
        List<BizScheduleSession> pool = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .in(BizScheduleSession::getSessionStatus, List.of("0", "1")));
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (SessionItemBo it : items) {
            if (it.getDate() == null || it.getStart() == null || it.getEnd() == null) continue;
            int s1 = toMin(it.getStart()), e1 = toMin(it.getEnd());
            for (BizScheduleSession ex : pool) {
                if (excludeId != null && excludeId.equals(ex.getId())) continue;
                if (ex.getSessionDate() == null || !ex.getSessionDate().toString().equals(it.getDate())) continue;
                if (ex.getStartTime() == null || ex.getEndTime() == null) continue;
                int s2 = toMin(ex.getStartTime()), e2 = toMin(ex.getEndTime());
                if (!(s1 < e2 && e1 > s2)) continue;   // 无重叠
                boolean sameTarget = Objects.equals(ex.getTargetType(), targetType)
                    && Objects.equals(ex.getTargetId(), targetId);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("date", it.getDate());
                c.put("start", it.getStart());
                c.put("end", it.getEnd());
                c.put("kind", sameTarget ? "学生撞场" : "老师撞场");
                c.put("withSessionId", String.valueOf(ex.getId()));
                c.put("withTitle", titleOf(ex));
                out.add(c);
            }
        }
        return out;
    }

    // ─────────────────────── 批量排课 ───────────────────────

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batch(SessionBatchBo bo) {
        List<Map<String, Object>> conflicts = detectConflicts(bo.getTargetType(), bo.getTargetId(), bo.getItems(), null);
        boolean force = Boolean.TRUE.equals(bo.getForce());
        Map<String, Object> r = new LinkedHashMap<>();
        if (!conflicts.isEmpty() && !force) {
            r.put("created", new ArrayList<>());
            r.put("conflicts", conflicts);
            return r;
        }

        // autoBind：按 lesson_seq 顺序取未排课次
        List<Long> autoLessonQueue = new ArrayList<>();
        int autoIdx = 0;
        boolean autoBind = !Boolean.FALSE.equals(bo.getAutoBind()) && bo.getPlanId() != null;
        if (autoBind) {
            List<BizCoursePlanLesson> lessons = lessonMapper.selectList(new LambdaQueryWrapper<BizCoursePlanLesson>()
                .eq(BizCoursePlanLesson::getPlanId, bo.getPlanId()).orderByAsc(BizCoursePlanLesson::getLessonSeq));
            List<Long> bound = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                    .eq(BizScheduleSession::getTargetType, bo.getTargetType())
                    .eq(BizScheduleSession::getTargetId, bo.getTargetId())
                    .eq(BizScheduleSession::getPlanId, bo.getPlanId())
                    .isNotNull(BizScheduleSession::getPlanLessonId))
                .stream().map(BizScheduleSession::getPlanLessonId).toList();
            for (BizCoursePlanLesson l : lessons) {
                if (!bound.contains(l.getId())) autoLessonQueue.add(l.getId());
            }
        }

        List<Map<String, Object>> created = new ArrayList<>();
        if (bo.getItems() != null) {
            for (SessionItemBo it : bo.getItems()) {
                BizScheduleSession s = new BizScheduleSession();
                s.setTargetType(bo.getTargetType());
                s.setTargetId(bo.getTargetId());
                s.setPlanId(bo.getPlanId());
                s.setSessionDate(LocalDate.parse(it.getDate()));
                s.setStartTime(it.getStart());
                s.setEndTime(it.getEnd());
                String type = it.getSessionType() == null ? "1" : it.getSessionType();
                s.setSessionType(type);
                s.setSessionStatus("0");
                s.setPrepStatus("0");
                s.setLessonLocked("0");
                s.setExternalTitle(it.getExternalTitle());
                s.setNote(it.getNote());
                Long lessonId = it.getPlanLessonId();
                if (lessonId == null && autoBind && !"3".equals(type) && autoIdx < autoLessonQueue.size()) {
                    lessonId = autoLessonQueue.get(autoIdx++);
                }
                s.setPlanLessonId(lessonId);
                sessionMapper.insert(s);
                created.add(sessionVo(s));
            }
        }
        r.put("created", created);
        r.put("conflicts", conflicts);
        return r;
    }

    // ─────────────────────── 顺延（leave/cancel） ───────────────────────

    /** 请假/取消 + 触发顺延（契约§五-2）。newStatus='2' 请假 / '3' 取消。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> leaveOrCancel(Long sessionId, String newStatus) {
        BizScheduleSession event = sessionMapper.selectById(sessionId);
        if (event == null) throw new ServiceException("场次不存在");
        event.setSessionStatus(newStatus);
        sessionMapper.updateById(event);

        List<Map<String, Object>> deferred = new ArrayList<>();
        List<String> overflow = new ArrayList<>();

        Long freedLesson = event.getPlanLessonId();
        if (freedLesson != null && event.getPlanId() != null) {
            // 范围锁：该对象该计划、日期在其后、status='0'，按日期+开始时间排序
            List<BizScheduleSession> future = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                    .eq(BizScheduleSession::getTargetType, event.getTargetType())
                    .eq(BizScheduleSession::getTargetId, event.getTargetId())
                    .eq(BizScheduleSession::getPlanId, event.getPlanId())
                    .eq(BizScheduleSession::getSessionStatus, "0")
                    .gt(BizScheduleSession::getSessionDate, event.getSessionDate()))
                .stream()
                .sorted(Comparator.comparing(BizScheduleSession::getSessionDate)
                    .thenComparing(x -> x.getStartTime() == null ? "" : x.getStartTime()))
                .toList();

            // 非锁定场次 = 可续排槽位；锁定场次保持原课次并被跳过
            List<BizScheduleSession> nonLocked = future.stream()
                .filter(x -> !"1".equals(x.getLessonLocked())).toList();

            // lessonQueue = [freedLesson] + 各非锁定场次原课次
            List<Long> lessonQueue = new ArrayList<>();
            lessonQueue.add(freedLesson);
            for (BizScheduleSession s : nonLocked) lessonQueue.add(s.getPlanLessonId());

            for (int i = 0; i < nonLocked.size(); i++) {
                BizScheduleSession s = nonLocked.get(i);
                Long newLesson = lessonQueue.get(i);
                if (!Objects.equals(s.getPlanLessonId(), newLesson)) {
                    s.setPlanLessonId(newLesson);
                    sessionMapper.updateById(s);
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("sessionId", String.valueOf(s.getId()));
                    d.put("newLessonId", newLesson == null ? null : String.valueOf(newLesson));
                    deferred.add(d);
                }
            }
            // 末位课次悬空 → overflow
            Long leftover = lessonQueue.get(lessonQueue.size() - 1);
            if (leftover != null) {
                BizCoursePlanLesson ll = lessonMapper.selectById(leftover);
                if (ll != null) {
                    overflow.add("第" + ll.getLessonSeq() + "次·" + (ll.getTitle() == null ? "" : ll.getTitle()) + " 需补排");
                }
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("deferred", deferred);
        r.put("overflow", overflow);
        return r;
    }

    // ─────────────────────── 单场次操作 ───────────────────────

    public void markDone(Long id) {
        BizScheduleSession s = require(id);
        s.setSessionStatus("1");
        sessionMapper.updateById(s);
    }

    public void lock(Long id, boolean locked) {
        BizScheduleSession s = require(id);
        s.setLessonLocked(locked ? "1" : "0");
        sessionMapper.updateById(s);
    }

    /** 通用改：改期(改时间不触发顺延)/note/rebind(改绑只改本场)。 */
    public void update(Long id, SessionUpdateBo bo) {
        BizScheduleSession s = require(id);
        if (bo.getDate() != null) s.setSessionDate(LocalDate.parse(bo.getDate()));
        if (bo.getStart() != null) s.setStartTime(bo.getStart());
        if (bo.getEnd() != null) s.setEndTime(bo.getEnd());
        if (bo.getNote() != null) s.setNote(bo.getNote());
        if (bo.getPlanLessonId() != null) s.setPlanLessonId(bo.getPlanLessonId());
        sessionMapper.updateById(s);
    }

    private BizScheduleSession require(Long id) {
        BizScheduleSession s = sessionMapper.selectById(id);
        if (s == null) throw new ServiceException("场次不存在");
        return s;
    }

    // ─────────────────────── 读 ───────────────────────

    /** 月历数据（对象名/色、时间、planLesson 标题、type、prep_status；外部占位无备课点）。 */
    public List<Map<String, Object>> calendar(String start, String end, Long targetId) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizScheduleSession> w = new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .ge(start != null, BizScheduleSession::getSessionDate, start == null ? null : LocalDate.parse(start))
            .le(end != null, BizScheduleSession::getSessionDate, end == null ? null : LocalDate.parse(end))
            .eq(targetId != null, BizScheduleSession::getTargetId, targetId)
            .orderByAsc(BizScheduleSession::getSessionDate);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizScheduleSession s : sessionMapper.selectList(w)) {
            Map<String, Object> m = sessionVo(s);
            out.add(m);
        }
        return out;
    }

    /** 场次表。 */
    public List<Map<String, Object>> page(Long targetId, String status) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizScheduleSession> w = new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .eq(targetId != null, BizScheduleSession::getTargetId, targetId)
            .eq(status != null && !status.isBlank(), BizScheduleSession::getSessionStatus, status)
            .orderByDesc(BizScheduleSession::getSessionDate);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizScheduleSession s : sessionMapper.selectList(w)) out.add(sessionVo(s));
        return out;
    }

    /** 概览统计。 */
    public Map<String, Object> statOverview() {
        Long uid = LoginHelper.getUserId();
        long studentCount = studentMapper.selectCount(new LambdaQueryWrapper<BizStudent>()
            .eq(BizStudent::getCreateBy, uid).eq(BizStudent::getArchived, "0"));
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        long weekSessionCount = sessionMapper.selectCount(new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .ge(BizScheduleSession::getSessionDate, monday)
            .le(BizScheduleSession::getSessionDate, sunday));
        long todoPrepCount = prepTodo(7).size();
        long myQuestionCount = questionMapper.selectCount(new QueryWrapper<BizQuestion>()
            .eq("create_user", uid).ne("status", "2"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentCount", studentCount);
        m.put("weekSessionCount", weekSessionCount);
        m.put("todoPrepCount", todoPrepCount);
        m.put("myQuestionCount", myQuestionCount);
        return m;
    }

    /**
     * 待备清单：今天起 N 个自然日（含今天）内、status='0'、type≠'3'、prep_status∈{'0','1'}。
     * G8 边界：第 N 天在内、第 N+1 天不在。
     */
    public List<Map<String, Object>> prepTodo(int days) {
        Long uid = LoginHelper.getUserId();
        LocalDate today = LocalDate.now();
        LocalDate endDay = today.plusDays(days - 1L);
        LambdaQueryWrapper<BizScheduleSession> w = new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .ge(BizScheduleSession::getSessionDate, today)
            .le(BizScheduleSession::getSessionDate, endDay)
            .eq(BizScheduleSession::getSessionStatus, "0")
            .ne(BizScheduleSession::getSessionType, "3")
            .in(BizScheduleSession::getPrepStatus, List.of("0", "1"))
            .orderByAsc(BizScheduleSession::getSessionDate);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizScheduleSession s : sessionMapper.selectList(w)) out.add(sessionVo(s));
        return out;
    }

    // ─────────────────────── VO ───────────────────────

    private Map<String, Object> sessionVo(BizScheduleSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("targetType", s.getTargetType());
        m.put("targetId", String.valueOf(s.getTargetId()));
        m.put("targetName", targetName(s.getTargetType(), s.getTargetId()));
        m.put("targetColor", targetColor(s.getTargetType(), s.getTargetId()));
        m.put("planId", s.getPlanId() == null ? null : String.valueOf(s.getPlanId()));
        m.put("planLessonId", s.getPlanLessonId() == null ? null : String.valueOf(s.getPlanLessonId()));
        String lessonTitle = titleOf(s);
        m.put("lessonTitle", lessonTitle);
        m.put("planLessonTitle", lessonTitle);
        m.put("date", String.valueOf(s.getSessionDate()));
        m.put("start", s.getStartTime());
        m.put("end", s.getEndTime());
        m.put("sessionType", s.getSessionType());
        m.put("sessionStatus", s.getSessionStatus());
        // 外部占位无备课点
        m.put("prepStatus", "3".equals(s.getSessionType()) ? null : s.getPrepStatus());
        m.put("lessonLocked", s.getLessonLocked());
        m.put("externalTitle", s.getExternalTitle());
        m.put("note", s.getNote());
        return m;
    }

    private String titleOf(BizScheduleSession s) {
        if ("3".equals(s.getSessionType())) return s.getExternalTitle();
        if (s.getPlanLessonId() != null) {
            BizCoursePlanLesson l = lessonMapper.selectById(s.getPlanLessonId());
            if (l != null && l.getTitle() != null) return l.getTitle();
        }
        return "2".equals(s.getSessionType()) ? "测试" : "正课";
    }

    private String targetName(String type, Long id) {
        if (id == null) return null;
        if ("1".equals(type)) {
            BizClass c = classMapper.selectById(id);
            return c == null ? null : c.getName();
        }
        BizStudent s = studentMapper.selectById(id);
        return s == null ? null : s.getName();
    }

    private String targetColor(String type, Long id) {
        if (id == null) return null;
        if ("1".equals(type)) {
            BizClass c = classMapper.selectById(id);
            return c == null ? null : c.getColor();
        }
        BizStudent s = studentMapper.selectById(id);
        return s == null ? null : s.getColor();
    }

    private int toMin(String t) {
        if (t == null || t.isBlank()) return -1;
        String[] p = t.split(":");
        int h = Integer.parseInt(p[0].trim());
        int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
        return h * 60 + m;
    }

    public BizScheduleSession getSession(Long id) {
        return sessionMapper.selectById(id);
    }
}
