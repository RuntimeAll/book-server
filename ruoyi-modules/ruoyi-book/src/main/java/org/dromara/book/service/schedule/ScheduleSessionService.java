package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.dromara.book.util.EduTermUtil;
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
 * <p>batch+autoBind、conflict-check（老师/学生撞场）、leave/cancel（🔄 PRD-018 D6 <b>顺延已删</b>，
 * 只改本场状态 + 释放课次回池 M8）、销假 revoke-leave、改期、mark-done/lock/unlock、14 天细备窗口。
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
    private final PaperSlotService paperSlotService;
    /**
     * PRD-015 AC6 冲正钩子：请假/取消已结算场次时返还课时课费。
     * 🔴 单向依赖（本类 → SettlementService），反向绝不加，否则构造注入成环。
     */
    private final SettlementService settlementService;

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
                // 🔴 与 validateItems 的 parseDateOr400 口径统一（PRD-015 顺手修）：校验期 trim 过、
                //   插入期不 trim → " 2026-08-01" 这类带空白入参校验通过却在此抛 DateTimeParseException 裸 500。
                s.setSessionDate(LocalDate.parse(it.getDate().trim()));
                s.setStartTime(it.getStart());
                s.setEndTime(it.getEnd());
                String type = it.getSessionType() == null ? "1" : it.getSessionType();
                s.setSessionType(type);
                s.setSessionStatus("0");
                s.setPrepStatus("0");
                s.setLessonLocked("0");
                s.setExternalTitle(it.getExternalTitle());
                s.setNote(it.getNote());
                // 学科归位课程安排层(2026-07-11)：显式传则存，空=展示时兜底 计划→对象
                s.setSubject(EduTermUtil.normalizeSubject(it.getSubject()));
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

    // ─────────────────────── 请假 / 取消 / 销假 ───────────────────────

    /**
     * 请假/取消（🔄 <b>PRD-018 D6：自动顺延已整段删除</b>）+ <b>结算冲正</b>（PRD-015 AC6）。
     * newStatus='2' 请假 / '3' 取消。
     *
     * <p>🔴 <b>D6 删顺延</b>（PRD §2 减法清单，「标题错位事故元凶」）：取消/请假<b>只改本场状态</b>，
     * 绝不动其它场次的 plan_lesson_id。返回体 {@code deferred}/{@code overflow} 保留为空值
     * （FE/MCP 契约兼容位，批 3/4 各端跟随后可删）。
     *
     * <p>🔴 <b>M8 补位</b>：置 '2'/'3' 时把本场 {@code plan_lesson_id} <b>置空</b>——课次释放回池，
     * 之后 {@code batch(autoBind)} / {@code rebindPlan} 能重新排到它（两处 bound/occupied 口径都按
     * 「plan_lesson_id 非空」取，不看场次状态，不置空就等于这一课次被永久跳过）。
     * 原顺延块里唯一的 overflow 提示由「计划详情 unscheduledLessons」接住
     * （{@code CoursePlanService.detail}）。
     * ⚠️ 代价：销假恢复时原课次绑定<b>无法还原</b>（可能已被排给别的场次），需要时手工改绑。
     *
     * <p>R1b BUG-003 防重入闸：仅「已排」('0') 场次可操作。
     * 🔴 PRD-015 放行一类：<b>已结算</b>（session_status='1' 且 settle_status='1'）的场次可改请假/取消，
     * 此时同事务内走冲正（返还该场实扣课时课费 + settle_status='2'）。
     * 冲完 session_status 变 '2'/'3'、settle_status 变 '2' → 再点一次被本闸挡住，绝不双冲（G4 反性自检）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> leaveOrCancel(Long sessionId, String newStatus) {
        BizScheduleSession event = sessionMapper.selectById(sessionId);
        if (event == null) throw new ServiceException("场次不存在");
        boolean settled = "1".equals(event.getSessionStatus()) && "1".equals(event.getSettleStatus());
        if (!"0".equals(event.getSessionStatus()) && !settled) {
            throw new ServiceException("仅「已排」或「已结算」场次可请假/取消");
        }
        // PRD-015 冲正钩子：置 settle_status='2' 并插 '3' 流水（未结算场次返 null，零副作用）
        Map<String, Object> reversal = settlementService.reverseIfSettled(event,
            "2".equals(newStatus) ? "请假冲正" : "取消冲正");
        event.setSessionStatus(newStatus);
        // M8：课次释放回池（本场不再占着它），备课态随之归零
        Long freedLesson = event.getPlanLessonId();
        if (freedLesson != null) {
            event.setPlanLessonId(null);
            event.setPrepStatus("0");
        }
        sessionMapper.updateById(event);
        if (freedLesson != null) {
            // 🔴 全局 updateStrategy=NOT_NULL（common-mybatis.yml:32）→ updateById 对 null 字段
            //    直接跳过，setPlanLessonId(null) 一个字都写不进去（实测 G6-e 挂在这）。
            //    置 NULL 必须走 UpdateWrapper 显式 set。
            sessionMapper.update(null, new LambdaUpdateWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getId, event.getId())
                .set(BizScheduleSession::getPlanLessonId, null));
        }

        Map<String, Object> r = new LinkedHashMap<>();
        // 🔄 D6：顺延已死，两个键恒空（契约兼容位）
        r.put("deferred", new ArrayList<>());
        r.put("overflow", new ArrayList<>());
        // M8 additive：本场释放掉的课次 id（FE 可提示「该课次已回池可重新排」）
        r.put("freedLessonId", freedLesson == null ? null : String.valueOf(freedLesson));
        // PRD-015 additive：冲正明细 {hours, amount, deletedShells, keptShells}；未结算场次为 null
        r.put("reversal", reversal);
        return r;
    }

    /**
     * 销假（PRD-018 AC5 / G5）：把<b>请假('2') / 取消('3')</b> 的场次一键恢复成「已上 + 未结」，
     * 并删掉该场的 '2' 扣课与 '3' 冲正流水对 → uk(session_id, flow_type) 释放，可正常重新结算。
     *
     * <p>🔴 <b>余额不变</b>：流水对净额恒为零（冲正 = 扣课取负），删掉两行 hours_remain 仍是对的
     * （见 {@link SettlementService#dropSessionFlows}）。
     *
     * <p>幂等/守卫：非请假非取消（'0' 已排 / '1' 已上）→ 友好 400，重复销假第二次即被此闸挡住；
     * 已结算未冲正的场次（有 '2' 无 '3'）→ 400，避免删掉扣课行凭空还钱。
     *
     * <p>⚠️ 不恢复 plan_lesson_id：请假时课次已释放回池（M8），可能已被排给别的场次，需要时手工改绑。
     *
     * @return {sessionId, sessionStatus, settleStatus, removedFlows, freedLessonRestored:false}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revokeLeave(Long sessionId) {
        BizScheduleSession s = requireOwnedSession(sessionId);
        String st = s.getSessionStatus();
        if (!"2".equals(st) && !"3".equals(st)) {
            throw new ServiceException("该场次未请假/取消，无需销假（当前状态："
                + ("0".equals(st) ? "已排" : "1".equals(st) ? "已上" : st) + "）", 400);
        }
        int removed = settlementService.dropSessionFlows(s.getId());
        s.setSessionStatus("1");
        s.setSettleStatus("0");
        sessionMapper.updateById(s);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("sessionId", String.valueOf(s.getId()));
        r.put("sessionStatus", s.getSessionStatus());
        r.put("settleStatus", s.getSettleStatus());
        r.put("removedFlows", removed);
        // 课次绑定不还原（M8 释放回池后可能已被别的场次占用），FE 据此提示需要时手工改绑
        r.put("planLessonId", s.getPlanLessonId() == null ? null : String.valueOf(s.getPlanLessonId()));
        return r;
    }

    /**
     * 场次归属校验（不存在/无权同一文案，防存在性探测；同 SettlementService 口径）。
     *
     * <p>🔴 PRD-018 批4 收紧：<b>{@code create_by IS NULL} 视为无权</b>（原先放行）。
     * 归属未知的历史行不该被任意登录用户销假/改动；{@code uid == null}（内部无门禁口）仍照旧放行。
     */
    private BizScheduleSession requireOwnedSession(Long id) {
        BizScheduleSession s = sessionMapper.selectById(id);
        if (s == null) {
            throw new ServiceException("场次不存在或无权访问", 403);
        }
        Long uid = LoginHelper.getUserId();
        if (uid != null && !uid.equals(s.getCreateBy())) {
            throw new ServiceException("场次不存在或无权访问", 403);
        }
        return s;
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
                // 🔴 全局 updateStrategy=NOT_NULL（common-mybatis.yml:32）→ updateById 跳过 null 字段，
                //    上面的 setPlanLessonId(null) 一个字都写不进去：返回体 unbound 计了数、库里那些多余场次
                //    却还绑着旧课次（先于 PRD-018 存在的 bug，批3 顺手修）。置 NULL 必须走 UpdateWrapper。
                sessionMapper.update(null, new LambdaUpdateWrapper<BizScheduleSession>()
                    .eq(BizScheduleSession::getId, s.getId())
                    .set(BizScheduleSession::getPlanLessonId, null));
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

    /**
     * 通用改：改期/note/content/rebind(改绑只改本场，plan_id 随课次同步 = R1b S4)。
     * 🔄 PRD-018：改期<b>不触发顺延</b>（顺延本身已删）；新增 content = 这节课实际讲了什么（≤200 字）。
     */
    public void update(Long id, SessionUpdateBo bo) {
        BizScheduleSession s = require(id);
        // trim 口径同批量排课（PRD-015 顺手修）
        if (bo.getDate() != null) s.setSessionDate(LocalDate.parse(bo.getDate().trim()));
        if (bo.getStart() != null) s.setStartTime(bo.getStart());
        if (bo.getEnd() != null) s.setEndTime(bo.getEnd());
        if (bo.getNote() != null) s.setNote(bo.getNote());
        // PRD-018 ③：传空串 = 清空（老师撤回写错的内容）；超 200 字截断，别让 1406 把整次改期连坐
        boolean clearContent = false;
        if (bo.getContent() != null) {
            String c = bo.getContent().trim();
            clearContent = c.isEmpty();
            s.setContent(clearContent ? null : (c.length() > 200 ? c.substring(0, 200) : c));
        }
        if (bo.getSubject() != null) s.setSubject(EduTermUtil.normalizeSubject(bo.getSubject()));
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
        if (clearContent) {
            // 同 leaveOrCancel：全局 updateStrategy=NOT_NULL，置 NULL 必须走 UpdateWrapper
            sessionMapper.update(null, new LambdaUpdateWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getId, s.getId())
                .set(BizScheduleSession::getContent, null));
        }
    }

    private BizScheduleSession require(Long id) {
        BizScheduleSession s = sessionMapper.selectById(id);
        if (s == null) throw new ServiceException("场次不存在");
        return s;
    }

    /**
     * 硬删场次（移除课程，2026-07 新增）。🔴 归属校验：仅本人创建的场次可删，防水平越权（IDOR）。
     * biz_schedule_session 无逻辑删列 → deleteById 为物理删除，整行移除、不可恢复
     * （区别于 leaveOrCancel 的软取消 status='3'，那种行仍在）。无绑定计划亦不触发顺延。
     */
    public void delete(Long id) {
        BizScheduleSession s = require(id);
        Long uid = LoginHelper.getUserId();
        if (uid != null && s.getCreateBy() != null && !uid.equals(s.getCreateBy())) {
            throw new ServiceException("无权删除他人的场次");
        }
        sessionMapper.deleteById(id);
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
        // PRD-B-101：备课态改 lesson.paper_slots 实时推导，不再有 DB 列可筛 →
        // 拉候选后按 sessionVo 推导态在内存过滤 prepStatus∈{'0','1'}（课次量级小，无 N+1 顾虑）。
        LambdaQueryWrapper<BizScheduleSession> w = new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .ge(BizScheduleSession::getSessionDate, today)
            .le(BizScheduleSession::getSessionDate, endDay)
            .eq(BizScheduleSession::getSessionStatus, "0")
            .ne(BizScheduleSession::getSessionType, "3")
            .orderByAsc(BizScheduleSession::getSessionDate);
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizScheduleSession s : sessionMapper.selectList(w)) {
            Map<String, Object> vo = sessionVo(s);
            Object ps = vo.get("prepStatus");
            if ("0".equals(ps) || "1".equals(ps)) out.add(vo);
        }
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
        // 外部占位无备课点；PRD-003 D7：备课态唯一权威 = 该场次课次的专项材料位 lesson.special_ids
        // 实时推导（B-101 卷位链 D7 退役，不再读 paper_slots 也不读缓存列 session.prep_status；
        // 无课次/无材料 → '0'）。修「plans 页 vs 首页提醒/日程/月历双权威分裂」。
        m.put("prepStatus", "3".equals(s.getSessionType()) ? null
            : paperSlotService.deriveStatusFromMaterials(lesson == null ? null : lesson.getSpecialIds(),
                lesson == null ? null : lesson.getBookNodeIds()));
        m.put("lessonLocked", s.getLessonLocked());
        // PRD-015：结算态徽标（'0' 未结 / '1' 已结 / '2' 已冲正；旧数据 NULL 视为未结）
        m.put("settleStatus", s.getSettleStatus() == null ? "0" : s.getSettleStatus());
        // 已结场次带上实扣快照，FE 冲正确认文案「将返还 X 课时 / ¥Y」直接吃（未结不查库）
        m.put("settled", "1".equals(s.getSettleStatus()) ? settlementService.settledSnapshot(s.getId()) : null);
        String subj = resolveSubject(s);
        m.put("subject", subj);
        m.put("subjectLabel", EduTermUtil.subjectLabel(subj));
        m.put("externalTitle", s.getExternalTitle());
        m.put("note", s.getNote());
        // PRD-018 ③ additive：这节课实际讲了什么（台账「内容」列首选取值）
        m.put("content", s.getContent());
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

    /** 学科兜底链：场次显式 → 计划.subject → 对象.subject（学科归位课程安排层 2026-07-11）。 */
    private String resolveSubject(BizScheduleSession s) {
        if (s.getSubject() != null && !s.getSubject().isBlank()) return s.getSubject();
        if (s.getPlanId() != null) {
            BizCoursePlan p = planMapper.selectById(s.getPlanId());
            if (p != null && p.getSubject() != null && !p.getSubject().isBlank()) return p.getSubject();
        }
        return targetSubject(s.getTargetType(), s.getTargetId());
    }

    private String targetSubject(String type, Long id) {
        if (id == null) return null;
        if ("1".equals(type)) {
            BizClass c = classMapper.selectById(id);
            return c == null ? null : c.getSubject();
        }
        BizStudent st = studentMapper.selectById(id);
        return st == null ? null : st.getSubject();
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
