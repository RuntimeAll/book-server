package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ConflictCheckBo;
import org.dromara.book.domain.bo.SessionBatchBo;
import org.dromara.book.domain.bo.SessionItemBo;
import org.dromara.book.domain.bo.SessionUpdateBo;
import org.dromara.book.domain.entity.BizClass;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizPrepPack;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizClassMapper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizCoursePlanMapper;
import org.dromara.book.mapper.BizPrepPackMapper;
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
    private final BizCoursePlanMapper planMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizStudentMapper studentMapper;
    private final BizClassMapper classMapper;
    private final BizQuestionMapper questionMapper;
    private final BizPrepPackMapper prepPackMapper;

    /**
     * R1b S3 反向回填：场次新绑定/换绑到已有备课包的课次时，session.prep_status（日历色点缓存）
     * 按该课次包状态回填。一次 in 查询出 lessonId → pack.status 映射（无包课次不入 map，取值按 '0' 兜底）。
     */
    private Map<Long, String> lessonPackStatus(List<Long> lessonIds) {
        Map<Long, String> map = new LinkedHashMap<>();
        List<Long> ids = lessonIds == null ? List.of()
            : lessonIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return map;
        for (BizPrepPack p : prepPackMapper.selectList(new LambdaQueryWrapper<BizPrepPack>()
            .in(BizPrepPack::getPlanLessonId, ids))) {
            map.put(p.getPlanLessonId(), p.getStatus());
        }
        return map;
    }

    // ─────────────────────── 冲突检测 ───────────────────────

    /** 预检冲突。 */
    public Map<String, Object> conflictCheck(String targetType, Long targetId, List<SessionItemBo> items) {
        validateItems(items);
        List<Map<String, Object>> conflicts = detectConflicts(targetType, targetId, items, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conflicts", conflicts);
        return r;
    }

    /**
     * 入参校验：非法日期/时间（None/空/格式错）→ 友好 400（说清哪条 item 哪个字段），不再抛 500 未知异常。
     * date 必填且 yyyy-MM-dd；start/end 必填且 HH:mm[:ss] 且 start&lt;end。
     */
    private void validateItems(List<SessionItemBo> items) {
        if (items == null || items.isEmpty()) {
            throw new ServiceException("排课 items 不能为空", 400);
        }
        for (int i = 0; i < items.size(); i++) {
            SessionItemBo it = items.get(i);
            int n = i + 1;
            if (it == null) {
                throw new ServiceException("第" + n + "条排课为空", 400);
            }
            parseDateOr400(it.getDate(), n, "date");
            int s = parseTimeOr400(it.getStart(), n, "start");
            int e = parseTimeOr400(it.getEnd(), n, "end");
            if (s >= e) {
                throw new ServiceException("第" + n + "条排课时间无效：start(" + it.getStart()
                    + ") 必须早于 end(" + it.getEnd() + ")", 400);
            }
        }
    }

    private void parseDateOr400(String date, int n, String field) {
        if (date == null || date.isBlank()) {
            throw new ServiceException("第" + n + "条排课缺少 " + field + " 字段", 400);
        }
        try {
            LocalDate.parse(date.trim());
        } catch (Exception ex) {
            throw new ServiceException("第" + n + "条排课 " + field + " 格式错误（需 yyyy-MM-dd）：" + date, 400);
        }
    }

    private int parseTimeOr400(String t, int n, String field) {
        if (t == null || t.isBlank()) {
            throw new ServiceException("第" + n + "条排课缺少 " + field + " 字段", 400);
        }
        String[] p = t.trim().split(":");
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                throw new NumberFormatException();
            }
            return h * 60 + m;
        } catch (Exception ex) {
            throw new ServiceException("第" + n + "条排课 " + field + " 时间格式错误（需 HH:mm）：" + t, 400);
        }
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
        validateItems(bo.getItems());
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

        // R1b S3：候选课次（autoBind 队列 + 显式指定）一次性取包状态，供新场次 prep_status 回填
        List<Long> candidateLessons = new ArrayList<>(autoLessonQueue);
        if (bo.getItems() != null) {
            for (SessionItemBo it : bo.getItems()) {
                if (it != null && it.getPlanLessonId() != null) candidateLessons.add(it.getPlanLessonId());
            }
        }
        Map<Long, String> packStatus = lessonPackStatus(candidateLessons);

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
                // R1b S3 反向回填：绑到已有包的课次（先装包后排课）→ prep_status 按包状态回填
                if (lessonId != null && packStatus.containsKey(lessonId)) {
                    s.setPrepStatus(packStatus.get(lessonId));
                }
                sessionMapper.insert(s);
                created.add(sessionVo(s));
            }
        }
        r.put("created", created);
        r.put("conflicts", conflicts);
        return r;
    }

    // ─────────────────────── 顺延（leave/cancel） ───────────────────────

    /**
     * 请假/取消 + 触发顺延（契约§五-2）。newStatus='2' 请假 / '3' 取消。
     * R1b BUG-003 防重入闸：仅「已排」('0') 场次可操作——已取消/已上场次重复触发会把顺延链再跑一遍腐蚀数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> leaveOrCancel(Long sessionId, String newStatus) {
        BizScheduleSession event = sessionMapper.selectById(sessionId);
        if (event == null) throw new ServiceException("场次不存在");
        if (!"0".equals(event.getSessionStatus())) {
            throw new ServiceException("仅「已排」场次可请假/取消");
        }
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

            // R1b S3 遗留补丁：顺延换绑后 prep_status 按新课次包状态回填（无包/置空 → '0'）
            Map<Long, String> packStatus = lessonPackStatus(lessonQueue);

            for (int i = 0; i < nonLocked.size(); i++) {
                BizScheduleSession s = nonLocked.get(i);
                Long newLesson = lessonQueue.get(i);
                if (!Objects.equals(s.getPlanLessonId(), newLesson)) {
                    s.setPlanLessonId(newLesson);
                    s.setPrepStatus(newLesson == null ? "0" : packStatus.getOrDefault(newLesson, "0"));
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

    // ─────────────────────── 换绑计划（R1a·简版真做） ───────────────────────

    /**
     * 把对象的未上场次整体切到新计划（POST /teacher/schedule/target/{targetType}/{targetId}/rebind-plan）。
     *
     * <p>S1 校验：新计划必须归属该对象（target_type+target_id 匹配）。范围 = 该对象
     * status='0' 且日期&gt;=今天的场次（外部占位 type='3' 不参与），按 日期+开始时间 升序，
     * 依 lesson_seq 顺配新计划课次（剔除已被范围外场次占用的课次，防重复绑定）；
     * 场次多于课次的多余场次 plan_lesson_id 置 NULL；plan_id 一律同步改为新计划。
     *
     * @return {rebound: 绑上课次数, unbound: 置空数}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rebindPlan(String targetType, Long targetId, Long newPlanId) {
        if (newPlanId == null) {
            throw new ServiceException("newPlanId 不能为空", 400);
        }
        BizCoursePlan plan = planMapper.selectById(newPlanId);
        if (plan == null) {
            throw new ServiceException("新计划不存在");
        }
        if (!Objects.equals(plan.getTargetType(), targetType) || !Objects.equals(plan.getTargetId(), targetId)) {
            throw new ServiceException("新计划不归属该对象，不能换绑（S1）", 400);
        }

        LocalDate today = LocalDate.now();
        List<BizScheduleSession> future = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getTargetType, targetType)
                .eq(BizScheduleSession::getTargetId, targetId)
                .eq(BizScheduleSession::getSessionStatus, "0")
                .ne(BizScheduleSession::getSessionType, "3")
                .ge(BizScheduleSession::getSessionDate, today))
            .stream()
            .sorted(Comparator.comparing(BizScheduleSession::getSessionDate)
                .thenComparing(x -> x.getStartTime() == null ? "" : x.getStartTime()))
            .toList();

        // 新计划课次队列（lesson_seq 升序），剔除已被该对象换绑范围外场次占用的课次（如已上的场次）
        List<Long> futureIds = future.stream().map(BizScheduleSession::getId).toList();
        List<Long> occupied = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getTargetType, targetType)
                .eq(BizScheduleSession::getTargetId, targetId)
                .eq(BizScheduleSession::getPlanId, newPlanId)
                .isNotNull(BizScheduleSession::getPlanLessonId))
            .stream()
            .filter(x -> !futureIds.contains(x.getId()))
            .map(BizScheduleSession::getPlanLessonId)
            .toList();
        List<Long> lessonQueue = lessonMapper.selectList(new LambdaQueryWrapper<BizCoursePlanLesson>()
                .eq(BizCoursePlanLesson::getPlanId, newPlanId)
                .orderByAsc(BizCoursePlanLesson::getLessonSeq))
            .stream().map(BizCoursePlanLesson::getId)
            .filter(lid -> !occupied.contains(lid))
            .toList();

        // R1b S3：换绑后 prep_status 按新课次包状态回填（无包/置空 → '0'）
        Map<Long, String> packStatus = lessonPackStatus(lessonQueue);

        int rebound = 0;
        int unbound = 0;
        int idx = 0;
        for (BizScheduleSession s : future) {
            Long lid = idx < lessonQueue.size() ? lessonQueue.get(idx++) : null;
            s.setPlanId(newPlanId);
            s.setPlanLessonId(lid);
            s.setPrepStatus(lid == null ? "0" : packStatus.getOrDefault(lid, "0"));
            sessionMapper.updateById(s);
            if (lid != null) {
                rebound++;
            } else {
                unbound++;
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rebound", rebound);
        r.put("unbound", unbound);
        return r;
    }

    // ─────────────────────── 单场次操作 ───────────────────────

    /** 标已上。R1b BUG-003 防重入闸：仅「已排」('0') 场次可标。 */
    public void markDone(Long id) {
        BizScheduleSession s = require(id);
        if (!"0".equals(s.getSessionStatus())) {
            throw new ServiceException("该场次状态不可标记已上");
        }
        s.setSessionStatus("1");
        sessionMapper.updateById(s);
    }

    public void lock(Long id, boolean locked) {
        BizScheduleSession s = require(id);
        s.setLessonLocked(locked ? "1" : "0");
        sessionMapper.updateById(s);
    }

    /** 通用改：改期(改时间不触发顺延)/note/rebind(改绑只改本场，plan_id 随课次同步 = R1b S4)。 */
    public void update(Long id, SessionUpdateBo bo) {
        BizScheduleSession s = require(id);
        if (bo.getDate() != null) s.setSessionDate(LocalDate.parse(bo.getDate()));
        if (bo.getStart() != null) s.setStartTime(bo.getStart());
        if (bo.getEnd() != null) s.setEndTime(bo.getEnd());
        if (bo.getNote() != null) s.setNote(bo.getNote());
        if (bo.getPlanLessonId() != null) {
            // R1b S4：改绑课次时由 lesson 反查 plan_id 同步，杜绝 plan_lesson_id 与 plan_id 漂移
            BizCoursePlanLesson lesson = lessonMapper.selectById(bo.getPlanLessonId());
            if (lesson == null) {
                throw new ServiceException("课次不存在：planLessonId=" + bo.getPlanLessonId());
            }
            s.setPlanLessonId(bo.getPlanLessonId());
            s.setPlanId(lesson.getPlanId());
            // R1b S3 反向回填：改绑到已有包的课次 → prep_status 按包状态回填（无包='0'）
            BizPrepPack pack = prepPackMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
                .eq(BizPrepPack::getPlanLessonId, bo.getPlanLessonId()));
            s.setPrepStatus(pack == null ? "0" : pack.getStatus());
        }
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

    /** 场次表（FE PageResult&lt;SessionVO&gt; = {rows,total}）。 */
    public Map<String, Object> page(Long targetId, String status) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizScheduleSession> w = new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .eq(targetId != null, BizScheduleSession::getTargetId, targetId)
            .eq(status != null && !status.isBlank(), BizScheduleSession::getSessionStatus, status)
            // R1b S4：改升序，与 FE 本地升序对齐
            .orderByAsc(BizScheduleSession::getSessionDate);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizScheduleSession s : sessionMapper.selectList(w)) rows.add(sessionVo(s));
        return pageResult(rows);
    }

    /** FE PageResult&lt;T&gt; = {rows,total}（本域无服务端分页，total=行数）。 */
    static Map<String, Object> pageResult(List<Map<String, Object>> rows) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", rows);
        r.put("total", rows.size());
        return r;
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

    /**
     * 场次 VO（键名对齐 FE 冻结契约 schedule.ts）。SessionVO / CalendarSessionVO / PrepTodoVO 三处共用：
     * sessionDate/startTime/endTime（非 date/start/end）、color（非 targetColor）、lessonTitle。
     * R1a 补 lessonSeq（第 N 次课，可空）——FE 后续批显示"第N次课"用。
     */
    private Map<String, Object> sessionVo(BizScheduleSession s) {
        BizCoursePlanLesson lesson = s.getPlanLessonId() == null ? null : lessonMapper.selectById(s.getPlanLessonId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(s.getId()));
        m.put("targetType", s.getTargetType());
        m.put("targetId", String.valueOf(s.getTargetId()));
        m.put("targetName", targetName(s.getTargetType(), s.getTargetId()));
        m.put("color", targetColor(s.getTargetType(), s.getTargetId()));
        m.put("planId", s.getPlanId() == null ? null : String.valueOf(s.getPlanId()));
        m.put("planLessonId", s.getPlanLessonId() == null ? null : String.valueOf(s.getPlanLessonId()));
        m.put("lessonSeq", lesson == null ? null : lesson.getLessonSeq());
        m.put("lessonTitle", titleOf(s, lesson));
        m.put("sessionDate", String.valueOf(s.getSessionDate()));
        m.put("startTime", s.getStartTime());
        m.put("endTime", s.getEndTime());
        m.put("sessionType", s.getSessionType());
        m.put("sessionStatus", s.getSessionStatus());
        // 外部占位无备课点
        m.put("prepStatus", "3".equals(s.getSessionType()) ? null : s.getPrepStatus());
        m.put("lessonLocked", s.getLessonLocked());
        m.put("externalTitle", s.getExternalTitle());
        m.put("note", s.getNote());
        m.put("createTime", s.getCreateTime());
        m.put("updateTime", s.getUpdateTime());
        return m;
    }

    private String titleOf(BizScheduleSession s) {
        BizCoursePlanLesson l = s.getPlanLessonId() == null ? null : lessonMapper.selectById(s.getPlanLessonId());
        return titleOf(s, l);
    }

    /** 已取到课次实体时用本重载，避免重复查库。 */
    private String titleOf(BizScheduleSession s, BizCoursePlanLesson lesson) {
        if ("3".equals(s.getSessionType())) return s.getExternalTitle();
        if (lesson != null && lesson.getTitle() != null) return lesson.getTitle();
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
