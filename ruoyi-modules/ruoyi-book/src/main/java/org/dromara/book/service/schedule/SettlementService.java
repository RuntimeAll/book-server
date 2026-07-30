package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SettleBo;
import org.dromara.book.domain.bo.SettleItemBo;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizFeedbackSheet;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.domain.entity.BizTuitionAccount;
import org.dromara.book.domain.entity.BizTuitionFlow;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizCoursePlanMapper;
import org.dromara.book.mapper.BizFeedbackSheetMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.mapper.BizTuitionAccountMapper;
import org.dromara.book.mapper.BizTuitionFlowMapper;
import org.dromara.book.util.EduTermUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场次结算 Service（PRD-015 D4/D5/D12 + AC5/AC6，/teacher/schedule/settle**）。
 *
 * <p><b>一条线</b>（D1）：场次 = 交点。过点未结场次进「待结算」清单 → 老师一键确认 → 一次事务内
 * ①扣课流水（'2'，hours_delta=-实扣、amount_delta=-实扣×单价，含 after 快照）+ 更新余额
 * ②场次 session_status='1' + settle_status='1' ③按需建反馈壳（绑 session_id/plan_id，
 * lesson_seq=该计划现有反馈 max+1）。
 *
 * <p><b>只提醒不自动扣</b>（D4）：本类<b>没有任何定时任务</b>，pending 是读时查询；扣费唯一触发点 =
 * {@link #settle(SettleBo)}。改期、批量排课、归档联动取消（{@code ScheduleTargetService.cancelFutureSessions}
 * 只批改 session_status='0' 的未来场次）、请假顺延改绑（{@code ScheduleSessionService.leaveOrCancel}
 * 的 lessonQueue 重绑）<b>一律不动钱</b>。
 *
 * <p><b>可逆</b>（AC6）：已结场次改请假/取消 → {@link #reverseIfSettled} 在同一事务内插 '3' 冲正行
 * （按该场<b>实扣数</b>返还，不是按 1）+ 恢复余额 + settle_status='2'；空反馈壳删除、有内容的保留。
 *
 * <p><b>幂等</b>：uk(session_id, flow_type) 是最终守门——同场不可重复扣('2')、不可重复冲('3')。
 * 服务层先查后插给友好文案（「该场次已结算」），DuplicateKey 兜底转 skipped 而非 500。
 *
 * <p><b>逐场独立事务</b>：{@link #settle} 循环里经 {@code SpringUtils.getAopProxy(this)} 调
 * {@link #settleOne}（自调用不过代理 = 事务失效，RuoYi 既有写法），某场失败只该场回滚并 skipped。
 *
 * <p>🔴 余额只经 {@link TuitionAccountService#applyFlow} 变动（审计线铁律），本类不裸 UPDATE 余额列。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    /** 默认实扣课时（D5：一场一课时）。 */
    private static final BigDecimal DEFAULT_HOURS = BigDecimal.ONE;

    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanMapper planMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizStudentMapper studentMapper;
    private final BizTuitionAccountMapper accountMapper;
    private final BizTuitionFlowMapper flowMapper;
    private final BizFeedbackSheetMapper sheetMapper;
    private final TuitionAccountService accountService;

    // ─────────────────────────── 待结算清单 ───────────────────────────

    /**
     * 待结算清单（AC5 / V11）：<b>结束时间已过 && session_status='0' && settle_status='0'</b> 的本人场次，
     * 按日期+起始时间升序。
     *
     * <p>范围口径：只学生对象（班课不接账户/结算，PRD §9）、排除外部占位（'3' 只为避冲突，不收费）。
     * 无账户的场次<b>照常列出</b>但 price=null（FE 提示先开户；结算时该场 skipped）。
     *
     * <p>时间判定：日期 &lt; 今天 一律过点；日期 = 今天 则比 end_time（HH:mm 文本）与当前时刻。
     * 不建索引扫全表——本人场次量级小（le(session_date, today) 已收敛 + idx_settle）。
     */
    public List<Map<String, Object>> pending() {
        Long uid = LoginHelper.getUserId();
        LocalDate today = LocalDate.now();
        int nowMin = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();

        List<BizScheduleSession> list = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
            .eq(BizScheduleSession::getCreateBy, uid)
            .eq(BizScheduleSession::getTargetType, "0")
            .ne(BizScheduleSession::getSessionType, "3")
            .eq(BizScheduleSession::getSessionStatus, "0")
            .eq(BizScheduleSession::getSettleStatus, "0")
            .le(BizScheduleSession::getSessionDate, today));

        List<Map<String, Object>> out = new ArrayList<>();
        for (BizScheduleSession s : list) {
            if (s.getSessionDate() == null) continue;
            if (s.getSessionDate().isEqual(today) && toMin(s.getEndTime()) >= nowMin) {
                continue;   // 今天但还没下课 → 不算过点
            }
            out.add(pendingRow(s));
        }
        out.sort(Comparator.comparing(SettlementService::sortKey));
        return out;
    }

    /** 排序键：日期 + 起始时间（都是零填充文本，字典序=时间序）。 */
    private static String sortKey(Map<String, Object> row) {
        return String.valueOf(row.get("date")) + String.valueOf(row.get("start"));
    }

    /** 待结算一行（键名对齐 FE 契约 PendingSettlementVO；subjectLabel 为 additive 展示字段）。 */
    private Map<String, Object> pendingRow(BizScheduleSession s) {
        String subject = resolveSubject(s);
        BizTuitionAccount any = findAnyAccount(s.getTargetId(), subject);
        boolean disabled = any != null && TuitionAccountService.STATUS_DISABLED.equals(any.getStatus());
        BizTuitionAccount acc = disabled ? null : any;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", String.valueOf(s.getId()));
        m.put("date", String.valueOf(s.getSessionDate()));
        m.put("start", trimSec(s.getStartTime()));
        m.put("end", trimSec(s.getEndTime()));
        m.put("targetName", studentName(s.getTargetId()));
        m.put("subject", subject);
        m.put("subjectLabel", EduTermUtil.subjectLabel(subject));
        m.put("planLessonTitle", lessonTitle(s.getPlanLessonId()));
        // 🔴 无账户 = null（不是 0）：FE 据此提示「先开户」，别显示成 0 元误导
        m.put("price", acc == null ? null : num(acc.getLessonPrice()));
        // additive（bug 批 BUG-3/A）：null=没开户 / '0'=在用 / '1'=已停用——
        // 让 FE 把「没开户」和「停用了」两种 price=null 说清楚，别一律劝人去开户
        m.put("accountStatus", any == null ? null : any.getStatus());
        return m;
    }

    // ─────────────────────────── 一键结算 ───────────────────────────

    /**
     * 一键结算（AC5）：逐场独立事务，返回 {settled, feedbackSheetIds, skipped:[{sessionId,reason}]}。
     *
     * <p>skipped 场景：未开户 / 已结算（uk 幂等）/ 已冲正 / 班课 / 外部占位 / 场次不存在或无权。
     * 🔴 任一场 skipped 不影响其余场落账，也不返 500。
     */
    public Map<String, Object> settle(SettleBo bo) {
        List<SettleItemBo> items = bo == null ? null : bo.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("请选择要结算的场次（items 不能为空）", 400);
        }
        // 不传 genFeedback = true（与 FE 弹窗默认勾选一致，D12）
        boolean genFeedback = bo.getGenFeedback() == null || Boolean.TRUE.equals(bo.getGenFeedback());
        SettlementService self = SpringUtils.getAopProxy(this);

        int settled = 0;
        List<String> sheetIds = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (SettleItemBo it : items) {
            String sid = it == null || it.getSessionId() == null ? null : String.valueOf(it.getSessionId());
            try {
                Map<String, Object> one = self.settleOne(it, genFeedback);
                settled++;
                Object fb = one.get("feedbackSheetId");
                if (fb != null) sheetIds.add(String.valueOf(fb));
            } catch (ServiceException e) {
                skipped.add(skip(sid, e.getMessage()));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // uk(session_id,'2') 兜底：并发下两次结算撞唯一键 → 幂等拒绝，不是 500
                skipped.add(skip(sid, "该场次已结算"));
            } catch (Exception e) {
                skipped.add(skip(sid, "结算失败：" + e.getMessage()));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("settled", settled);
        r.put("feedbackSheetIds", sheetIds);
        r.put("skipped", skipped);
        return r;
    }

    private Map<String, Object> skip(String sessionId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", sessionId);
        m.put("reason", reason);
        return m;
    }

    /**
     * 单场结算（独立事务，三连原子）：扣课流水 + 场次已上/已结 + 反馈壳。
     * 🔴 public 且经 AOP 代理调用才有事务（见类注释）。
     *
     * @return {sessionId, hours, amount, feedbackSheetId?}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleOne(SettleItemBo it, boolean genFeedback) {
        if (it == null || it.getSessionId() == null) {
            throw new ServiceException("缺少 sessionId", 400);
        }
        BizScheduleSession s = requireOwnedSession(it.getSessionId());
        if ("1".equals(s.getTargetType())) {
            throw new ServiceException("班课暂不支持结算（PRD-015 §9）");
        }
        if ("3".equals(s.getSessionType())) {
            throw new ServiceException("外部占位无需结算");
        }
        if ("1".equals(s.getSettleStatus())) {
            throw new ServiceException("该场次已结算");
        }
        if ("2".equals(s.getSettleStatus())) {
            throw new ServiceException("该场次已冲正，如需重新结算请先联系维护（幂等键已占用）");
        }
        // uk(session_id,'2') 前置友好化：settle_status 与流水漂移时也不撞裸唯一键
        if (flowMapper.selectCount(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getSessionId, s.getId())
            .eq(BizTuitionFlow::getFlowType, TuitionAccountService.FLOW_DEDUCT)) > 0) {
            throw new ServiceException("该场次已结算");
        }

        String subject = resolveSubject(s);
        BizTuitionAccount acc = findAccount(s.getTargetId(), subject);
        if (acc == null) {
            // 「没开户」与「开了但停用」是两回事，skipped 里得说准（bug 批 BUG-3/A）
            BizTuitionAccount any = findAnyAccount(s.getTargetId(), subject);
            if (any != null) {
                throw new ServiceException("「" + EduTermUtil.subjectLabel(subject) + "」课时账户已停用，请先启用再结算");
            }
            throw new ServiceException("未开通「" + EduTermUtil.subjectLabel(subject) + "」课时账户，请先开户再结算");
        }
        BigDecimal hours = scale2(it.getHours() == null ? DEFAULT_HOURS : it.getHours());
        if (hours.signum() <= 0) {
            throw new ServiceException("实扣课时需大于 0", 400);
        }
        BigDecimal amount = scale2(hours.multiply(nz(acc.getLessonPrice())));

        // ① 扣课流水（唯一余额写入口，含 after 快照）
        accountService.applyFlow(acc, TuitionAccountService.FLOW_DEDUCT,
            hours.negate(), amount.negate(), s.getId(), noteOf(it));
        // ② 场次：已上 + 已结
        s.setSessionStatus("1");
        s.setSettleStatus("1");
        sessionMapper.updateById(s);
        // ③ 反馈壳（D12；无 plan_id 的散排场次不建——壳没有计划可挂，序号无从递增）
        Long sheetId = genFeedback && s.getPlanId() != null ? createFeedbackShell(s) : null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", String.valueOf(s.getId()));
        m.put("hours", num(hours));
        m.put("amount", num(amount));
        m.put("feedbackSheetId", sheetId == null ? null : String.valueOf(sheetId));
        return m;
    }

    /** 实际上课时间备注 → 流水 note（空则留 null，台账退回显示课次标题）。 */
    private String noteOf(SettleItemBo it) {
        String n = it.getTimeNote();
        return n == null || n.isBlank() ? null : n.trim();
    }

    /**
     * 建反馈壳（D7/D12）：绑 target_id/session_id/plan_id，lesson_seq = 该计划下现有反馈 max+1，
     * title=null（导出时按 D7「序号 · 上课日期」动态拼），rows_json='[]'（空壳，冲正时可安全删）。
     *
     * <p>幂等：该场次已有反馈单 → 直接返回既有 id，不重复建（结算被冲正后再结算的边界）。
     */
    private Long createFeedbackShell(BizScheduleSession s) {
        BizFeedbackSheet exist = sheetMapper.selectOne(new LambdaQueryWrapper<BizFeedbackSheet>()
            .eq(BizFeedbackSheet::getSessionId, s.getId())
            .last("LIMIT 1"));
        if (exist != null) {
            return exist.getId();
        }
        int max = 0;
        for (BizFeedbackSheet fb : sheetMapper.selectList(new LambdaQueryWrapper<BizFeedbackSheet>()
            .eq(BizFeedbackSheet::getCreateBy, LoginHelper.getUserId())
            .eq(BizFeedbackSheet::getPlanId, s.getPlanId()))) {
            if (fb.getLessonSeq() != null && fb.getLessonSeq() > max) {
                max = fb.getLessonSeq();
            }
        }
        BizFeedbackSheet e = new BizFeedbackSheet();
        e.setTargetId(s.getTargetId());
        e.setSessionId(s.getId());
        e.setPlanId(s.getPlanId());
        e.setLessonSeq(max + 1);
        e.setTitle(null);
        e.setLessonDate(s.getSessionDate());
        e.setRowsJson("[]");
        sheetMapper.insert(e);
        return e.getId();
    }

    // ─────────────────────────── 冲正（请假/取消钩子） ───────────────────────────

    /**
     * 冲正钩子（AC6）：已结算场次被改请假/取消时调用，<b>在调用方事务内</b>完成
     * ①按该场实扣数插 '3' 冲正行 + 恢复余额 ②入参 session 对象上置 settle_status='2'
     * （由调用方随 session_status 一并 updateById 落库）③空反馈壳删除、有内容保留。
     *
     * <p>🔴 只有 settle_status='1' 才动作，其余状态直接返回 null（未结算的请假不产生任何流水）。
     * uk(session_id,'3') 保证只冲一次；服务层先查后插给幂等短路。
     *
     * @param s      场次实体（调用方已取好、已做归属校验）
     * @param reason 冲正原因（写进流水 note，如「请假冲正」）
     * @return 冲正明细 {hours, amount, deletedShells, keptShells}；未结算返 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reverseIfSettled(BizScheduleSession s, String reason) {
        if (s == null || !"1".equals(s.getSettleStatus())) {
            return null;
        }
        // 已有冲正行 → 幂等短路（只补状态，不再插流水）
        if (flowMapper.selectCount(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getSessionId, s.getId())
            .eq(BizTuitionFlow::getFlowType, TuitionAccountService.FLOW_REVERSE)) > 0) {
            s.setSettleStatus("2");
            return null;
        }
        BizTuitionFlow deduct = flowMapper.selectOne(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getSessionId, s.getId())
            .eq(BizTuitionFlow::getFlowType, TuitionAccountService.FLOW_DEDUCT)
            .last("LIMIT 1"));
        if (deduct == null) {
            // 状态漂移（标了已结却无扣课行）：只纠状态，不凭空返还
            s.setSettleStatus("2");
            return null;
        }
        BizTuitionAccount acc = accountMapper.selectById(deduct.getAccountId());
        BigDecimal hours = nz(deduct.getHoursDelta()).negate();     // 实扣为负 → 返还为正
        BigDecimal amount = nz(deduct.getAmountDelta()).negate();
        if (acc != null) {
            accountService.applyFlow(acc, TuitionAccountService.FLOW_REVERSE,
                hours, amount, s.getId(), reason);
        }
        s.setSettleStatus("2");

        // 反馈壳处置（D12）：空壳删、有内容留
        int deleted = 0;
        int kept = 0;
        for (BizFeedbackSheet fb : sheetMapper.selectList(new LambdaQueryWrapper<BizFeedbackSheet>()
            .eq(BizFeedbackSheet::getSessionId, s.getId()))) {
            if (isEmptyRows(fb.getRowsJson())) {
                sheetMapper.deleteById(fb.getId());
                deleted++;
            } else {
                kept++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hours", num(hours));
        m.put("amount", num(amount));
        m.put("deletedShells", deleted);
        m.put("keptShells", kept);
        return m;
    }

    /** 空壳判定：rows_json 为空/空数组即视为「老师还没写内容」。 */
    private boolean isEmptyRows(String rowsJson) {
        if (rowsJson == null) return true;
        String t = rowsJson.trim();
        return t.isEmpty() || "[]".equals(t) || "null".equals(t);
    }

    /**
     * 该场次的结算快照（FE 冲正确认文案「将返还 X 课时 / ¥Y」用）。
     * settle_status='1' 且有扣课行才返 {hours, amount}，否则 null。
     */
    public Map<String, Object> settledSnapshot(Long sessionId) {
        if (sessionId == null) return null;
        BizTuitionFlow f = flowMapper.selectOne(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getSessionId, sessionId)
            .eq(BizTuitionFlow::getFlowType, TuitionAccountService.FLOW_DEDUCT)
            .last("LIMIT 1"));
        if (f == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hours", num(nz(f.getHoursDelta()).negate()));
        m.put("amount", num(nz(f.getAmountDelta()).negate()));
        return m;
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** 场次归属校验（不区分不存在/无权，防存在性探测；同 TuitionAccountService 口径）。 */
    private BizScheduleSession requireOwnedSession(Long id) {
        BizScheduleSession s = sessionMapper.selectById(id);
        if (s == null) {
            throw new ServiceException("场次不存在或无权访问", 403);
        }
        Long uid = LoginHelper.getUserId();
        if (uid != null && s.getCreateBy() != null && !uid.equals(s.getCreateBy())) {
            throw new ServiceException("场次不存在或无权访问", 403);
        }
        return s;
    }

    /**
     * 学生×学科<b>在用</b>账户（owner 过滤 + 排除停用）；无账户或已停用返 null。
     * 🔴 bug 批 BUG-3/A：停用账户不参与结算取价与扣费——要继续上课先启用回来。
     * （冲正走 {@code accountMapper.selectById(deduct.accountId)}，不经这里，停用后旧账仍能退。）
     */
    private BizTuitionAccount findAccount(Long studentId, String subject) {
        BizTuitionAccount a = findAnyAccount(studentId, subject);
        return a != null && TuitionAccountService.STATUS_DISABLED.equals(a.getStatus()) ? null : a;
    }

    /** 学生×学科账户（含停用，owner 过滤）——用于把「没开户」与「已停用」两种情况分开说。 */
    private BizTuitionAccount findAnyAccount(Long studentId, String subject) {
        if (studentId == null || subject == null || subject.isBlank()) return null;
        return accountMapper.selectOne(new LambdaQueryWrapper<BizTuitionAccount>()
            .eq(BizTuitionAccount::getCreateBy, LoginHelper.getUserId())
            .eq(BizTuitionAccount::getStudentId, studentId)
            .eq(BizTuitionAccount::getSubject, subject)
            .last("LIMIT 1"));
    }

    /**
     * 学科兜底链：场次显式 → 计划.subject → 学生.subject（与
     * {@code ScheduleSessionService.resolveSubject} 同口径）。
     * 🔴 故意<b>复制</b>而非复用：ScheduleSessionService 已依赖本类（冲正钩子），
     * 反向依赖会成构造注入循环。改口径时两处同步。
     */
    private String resolveSubject(BizScheduleSession s) {
        if (s.getSubject() != null && !s.getSubject().isBlank()) {
            return s.getSubject();
        }
        if (s.getPlanId() != null) {
            BizCoursePlan p = planMapper.selectById(s.getPlanId());
            if (p != null && p.getSubject() != null && !p.getSubject().isBlank()) {
                return p.getSubject();
            }
        }
        BizStudent st = s.getTargetId() == null ? null : studentMapper.selectById(s.getTargetId());
        return st == null ? null : st.getSubject();
    }

    private String studentName(Long studentId) {
        BizStudent st = studentId == null ? null : studentMapper.selectById(studentId);
        return st == null ? null : st.getName();
    }

    private String lessonTitle(Long planLessonId) {
        if (planLessonId == null) return null;
        BizCoursePlanLesson l = lessonMapper.selectById(planLessonId);
        return l == null ? null : l.getTitle();
    }

    private String trimSec(String t) {
        return t != null && t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private int toMin(String t) {
        if (t == null || t.isBlank()) return 24 * 60;   // 无结束时间 → 当天不算过点（保守）
        String[] p = t.split(":");
        try {
            int h = Integer.parseInt(p[0].trim());
            int m = p.length > 1 ? Integer.parseInt(p[1].trim()) : 0;
            return h * 60 + m;
        } catch (Exception e) {
            return 24 * 60;
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal scale2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** 同 TuitionAccountService#num：全局 Jackson 把 BigDecimal 序列化成字符串，VO 出参统一转 Double。 */
    private Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
