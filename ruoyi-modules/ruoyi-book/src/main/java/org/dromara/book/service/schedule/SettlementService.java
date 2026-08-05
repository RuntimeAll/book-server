package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SettleBo;
import org.dromara.book.domain.bo.SettleItemBo;
import org.dromara.book.domain.entity.BizCoursePlan;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.domain.entity.BizStudentAccountLink;
import org.dromara.book.domain.entity.BizTuitionAccount;
import org.dromara.book.domain.entity.BizTuitionFlow;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizCoursePlanMapper;
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
 * <b>两连原子</b>（🔄 PRD-018 D10 由三连缩为两连）：
 * ①扣课流水（'2'，hours_delta=-实扣小时、amount_paid=-实扣×时薪）+ 更新余额缓存
 * ②场次 session_status='1' + settle_status='1'。
 *
 * <p>🔴 <b>D10 域间解耦（2026-08-05 拍板，架构级）</b>，本类落两条：
 * <ol>
 *   <li><b>撤反馈壳</b>：结算不再副作用式写反馈域（原 {@code createFeedbackShell} 整段删除）。
 *       反馈单独立建单，{@code lesson_seq} 不再写入时 max+1 定格。{@code SettleBo.genFeedback}
 *       字段保留但<b>忽略</b>（M5 兼容旧调用方），返回体 {@code feedbackSheetIds} 恒空数组。</li>
 *   <li><b>拆收费硬闸</b>：无账本 / 账本停用<b>不再抛异常</b> —— 场次照常标「已上 + 未结」
 *       （{@code session_status='1'}、{@code settle_status='0'}），只跳过扣课并在 skipped 里给准原因；
 *       该场继续留在 {@link #pending()} 清单里等补扣（开户后再结一次即可）。
 *       <b>教学事实不被收费状态锁死。</b></li>
 * </ol>
 *
 * <p>🔄 <b>PRD-018 拍板 D-a（扣多少）</b>：默认实扣 = {@code link.hours_per_lesson}（与家长约定的计价单位），
 * <b>不是</b> 场次起止时长——排课时段是日程，改期/拖拽/接送缓冲格子永不影响钱。
 * {@code pendingRow} 带 {@code plannedHours}，与 {@link #settleOne} 走<b>同一个</b>
 * {@link #plannedHours} 函数算出，FE/机器人默认值只认它（L4）；settle item 的 hours 参数保留人工覆盖。
 *
 * <p>🔄 <b>拍板 B2（历史金额冻结）</b>：扣课/冲正行把结算当时派生的金额写进 {@code flow.amount_paid}，
 * 之后改时薪不重算历史。冲正行 {@code occur_date} = 所冲场次日期（不是「今天」，M4）。
 *
 * <p><b>只提醒不自动扣</b>（D4）：本类<b>没有任何定时任务</b>，pending 是读时查询；扣费唯一触发点 =
 * {@link #settle(SettleBo)}。改期、批量排课、归档联动取消（{@code ScheduleTargetService.cancelFutureSessions}
 * 只批改 session_status='0' 的未来场次）、请假/取消释放课次（PRD-018 D6 顺延已删，
 * {@code ScheduleSessionService.leaveOrCancel} 只置状态 + 置空 plan_lesson_id）<b>一律不动钱</b>。
 *
 * <p><b>可逆</b>（AC6）：已结场次改请假/取消 → {@link #reverseIfSettled} 在同一事务内插 '3' 冲正行
 * （按该场<b>实扣数</b>返还，不是按 1）+ 恢复余额 + settle_status='2'。
 * 🔄 D10：结算既已不建壳，冲正也不再回头删壳（原空壳清理段一并撤除）。
 *
 * <p><b>销假</b>（PRD-018 AC5）：{@link #dropSessionFlows} 删掉该场 '2'+'3' 流水对
 * （净额为零 → 余额缓存不需变），释放 uk(session_id, flow_type) 使该场可重新结算。
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

    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanMapper planMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizStudentMapper studentMapper;
    private final BizTuitionAccountMapper accountMapper;
    private final BizTuitionFlowMapper flowMapper;
    private final TuitionAccountService accountService;

    // ─────────────────────────── 待结算清单 ───────────────────────────

    /**
     * 待结算清单（AC5 / V11）：<b>结束时间已过 && session_status∈{'0' 已排, '1' 已上} && settle_status='0'</b>
     * 的本人场次，按日期+起始时间升序。
     *
     * <p>🔄 <b>PRD-018 D10</b>：收「已上但未结」（'1'+'0'）—— 无账本/账本停用时结算会把场次标已上、
     * 跳过扣课，这些「待补扣」场次<b>必须继续留在本清单里</b>，否则开户后再也找不到它们。
     * 手工 mark-done 的场次同理（教学事实先落，钱后补）。
     *
     * <p>范围口径：只学生对象（班课不接账户/结算，PRD §9）、排除外部占位（'3' 只为避冲突，不收费）。
     * 无账户的场次<b>照常列出</b>但 price=null（FE 提示先开户）。
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
            .in(BizScheduleSession::getSessionStatus, List.of("0", "1"))
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
        BizStudentAccountLink link = accountService.findLink(s.getTargetId(), subject);
        BizTuitionAccount any = link == null ? null : accountMapper.selectById(link.getAccountId());
        boolean disabled = any != null && TuitionAccountService.STATUS_DISABLED.equals(any.getStatus());
        BizTuitionAccount acc = disabled ? null : any;
        BigDecimal hpl = accountService.hoursPerLessonOf(link);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", String.valueOf(s.getId()));
        m.put("date", String.valueOf(s.getSessionDate()));
        m.put("start", trimSec(s.getStartTime()));
        m.put("end", trimSec(s.getEndTime()));
        m.put("targetName", studentName(s.getTargetId()));
        m.put("subject", subject);
        m.put("subjectLabel", EduTermUtil.subjectLabel(subject));
        m.put("planLessonTitle", lessonTitle(s.getPlanLessonId()));
        // 🔴 无账本 = null（不是 0）：FE 据此提示「先开户」，别显示成 0 元误导
        // 🔄 M5 过渡兼容：price 仍是「元/节」派生值 = 时薪 × 每节时长；新字段 pricePerHour 是时薪
        m.put("price", acc == null ? null : num(scale2(nz(acc.getPricePerHour()).multiply(hpl))));
        m.put("pricePerHour", acc == null ? null : num(nz(acc.getPricePerHour())));
        m.put("hoursPerLesson", link == null ? null : num(hpl));
        // 🔴 L4：预计实扣，与 settleOne 同一个函数算出——FE/机器人默认值只认它，杜绝「看到的预扣≠真实扣款」
        m.put("plannedHours", num(plannedHours(link)));
        m.put("plannedAmount", acc == null ? null
            : num(scale2(plannedHours(link).multiply(nz(acc.getPricePerHour())))));
        m.put("accountId", any == null ? null : String.valueOf(any.getId()));
        // additive（bug 批 BUG-3/A）：null=没开户 / '0'=在用 / '1'=已停用——
        // 让 FE 把「没开户」和「停用了」两种 price=null 说清楚，别一律劝人去开户
        m.put("accountStatus", any == null ? null : any.getStatus());
        // additive（PRD-018 D10）：'0' 未上待结 / '1' 已上待补扣（结算时无账本跳过了扣课）
        m.put("sessionStatus", s.getSessionStatus());
        return m;
    }

    /**
     * 预计实扣小时（拍板 D-a，<b>settleOne 与 pendingRow 唯一共用口径</b>）= 该绑定的每节时长。
     * 无绑定时退回 1.00（占位展示；真结算会先在取账本那一步 skipped）。
     */
    private BigDecimal plannedHours(BizStudentAccountLink link) {
        return accountService.hoursPerLessonOf(link);
    }

    // ─────────────────────────── 一键结算 ───────────────────────────

    /**
     * 一键结算（AC5）：逐场独立事务，返回 {settled, feedbackSheetIds, skipped:[{sessionId,reason}]}。
     *
     * <p>skipped 场景：<b>未开户 / 账本停用（🔄 D10：这两种已标已上，只是没扣钱）</b> /
     * 已结算（uk 幂等）/ 已冲正 / 班课 / 外部占位 / 场次不存在或无权。
     * 🔴 任一场 skipped 不影响其余场落账，也不返 500。
     *
     * <p>🔄 D10：{@code feedbackSheetIds} <b>恒为空数组</b>（结算不再建反馈壳），键保留只为旧 FE/MCP
     * 读 {@code res.feedbackSheetIds?.length} 时不炸；{@code bo.genFeedback} 读都不读。
     */
    public Map<String, Object> settle(SettleBo bo) {
        List<SettleItemBo> items = bo == null ? null : bo.getItems();
        if (items == null || items.isEmpty()) {
            throw new ServiceException("请选择要结算的场次（items 不能为空）", 400);
        }
        SettlementService self = SpringUtils.getAopProxy(this);

        int settled = 0;
        List<String> sheetIds = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (SettleItemBo it : items) {
            String sid = it == null || it.getSessionId() == null ? null : String.valueOf(it.getSessionId());
            try {
                Map<String, Object> one = self.settleOne(it);
                // 🔄 D10：无账本/停用 → 场次已标已上（事务已提交），但没扣课 → 计 skipped 不计 settled
                Object pendingReason = one.get("skippedReason");
                if (pendingReason != null) {
                    skipped.add(skip(sid, String.valueOf(pendingReason)));
                } else {
                    settled++;
                }
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
        // 🔄 D10 撤壳后恒空（键保留 = M5 兼容位，批 3/4 各端跟随后可删）
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
     * 单场结算（独立事务，<b>两连原子</b>）：扣课流水 + 场次已上/已结。
     * 🔴 public 且经 AOP 代理调用才有事务（见类注释）。
     *
     * <p>🔄 <b>D10 拆收费硬闸</b>：无账本 / 账本停用 <b>不抛异常</b> —— 只标「已上 + 未结」
     * （{@code session_status='1'}、{@code settle_status} 保持 '0'）并返 {@code skippedReason}，
     * 该场继续留在待结算清单等补扣。教学事实先落，钱后补。
     *
     * @return {sessionId, hours, amount, feedbackSheetId(恒 null), skippedReason?}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> settleOne(SettleItemBo it) {
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
        BizStudentAccountLink link = accountService.findLink(s.getTargetId(), subject);
        BizTuitionAccount any = link == null ? null : accountMapper.selectById(link.getAccountId());
        BizTuitionAccount acc = any != null
            && TuitionAccountService.STATUS_DISABLED.equals(any.getStatus()) ? null : any;
        // 结算时顺手记「这节讲了什么」（PRD-018 ③；不传 = 不动原值）
        applyContent(s, it.getContent());

        // 🔄 D10：收费闸拆掉 —— 无账本/停用只跳过扣课，场次照常标「已上 + 未结」进待补扣
        if (acc == null) {
            s.setSessionStatus("1");
            // settle_status 保持 '0'：pending() 收 ('1','0') → 开户后再结一次即可补扣
            sessionMapper.updateById(s);
            // 「没开户」与「开了但停用」是两回事，skipped 里得说准（bug 批 BUG-3/A）
            String reason = any != null
                ? "「" + EduTermUtil.subjectLabel(subject) + "」课时账本已停用，已标已上待补扣"
                : "未开通「" + EduTermUtil.subjectLabel(subject) + "」课时账本，已标已上待补扣";
            Map<String, Object> sk = new LinkedHashMap<>();
            sk.put("sessionId", String.valueOf(s.getId()));
            sk.put("hours", null);
            sk.put("amount", null);
            sk.put("feedbackSheetId", null);
            sk.put("skippedReason", reason);
            return sk;
        }
        // 🔄 D-a：缺省扣时 = 每节时长（不是场次起止时长）；hours 入参仍可人工覆盖
        BigDecimal hours = scale2(it.getHours() == null ? plannedHours(link) : it.getHours());
        if (hours.signum() <= 0) {
            throw new ServiceException("实扣课时需大于 0", 400);
        }
        BigDecimal amount = scale2(hours.multiply(nz(acc.getPricePerHour())));

        // ① 扣课流水（唯一余额写入口）：occur_date=场次日期；amount_paid=当时派生金额（冻结，B2）
        accountService.applyFlow(acc, TuitionAccountService.FLOW_DEDUCT,
            hours.negate(), s.getId(), noteOf(it), s.getSessionDate(), amount.negate());
        // ② 场次：已上 + 已结
        s.setSessionStatus("1");
        s.setSettleStatus("1");
        sessionMapper.updateById(s);
        // 🔄 ③ 反馈壳已撤（D10）：结算不写反馈域，反馈单独立建单

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", String.valueOf(s.getId()));
        m.put("hours", num(hours));
        m.put("amount", num(amount));
        // 🔄 D10 撤壳后恒 null（键保留 = M5 兼容位）
        m.put("feedbackSheetId", null);
        return m;
    }

    /**
     * 结算时顺手写「这节课实际讲了什么」（PRD-018 ③）。空/不传 = 不动原值；超 200 字截断
     * （列宽 varchar(200)，超长直接 1406 会把整场结算连坐回滚，不值得）。
     */
    private void applyContent(BizScheduleSession s, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        String t = content.trim();
        s.setContent(t.length() > 200 ? t.substring(0, 200) : t);
    }

    /** 实际上课时间备注 → 流水 note（空则留 null，台账退回显示课次标题）。 */
    private String noteOf(SettleItemBo it) {
        String n = it.getTimeNote();
        return n == null || n.isBlank() ? null : n.trim();
    }

    // 🔄 createFeedbackShell 已整段删除（PRD-018 D10 域间解耦，2026-08-05）：
    //    结算不再副作用式写反馈域；反馈单独立建单，lesson_seq 不再写入时 max+1 定格。

    // ─────────────────────────── 冲正（请假/取消钩子） ───────────────────────────

    /**
     * 冲正钩子（AC6）：已结算场次被改请假/取消时调用，<b>在调用方事务内</b>完成
     * ①按该场实扣数插 '3' 冲正行 + 恢复余额 ②入参 session 对象上置 settle_status='2'
     * （由调用方随 session_status 一并 updateById 落库）。
     *
     * <p>🔄 <b>D10</b>：原「空反馈壳删除、有内容保留」段已撤 —— 结算既不建壳，冲正自然无壳可删，
     * 反馈单从此只由老师自己建/删。返回体 {@code deletedShells/keptShells} 保留但恒 0（M5 兼容位）。
     *
     * <p>🔴 只有 settle_status='1' 才动作，其余状态直接返回 null（未结算的请假不产生任何流水）。
     * uk(session_id,'3') 保证只冲一次；服务层先查后插给幂等短路。
     *
     * @param s      场次实体（调用方已取好、已做归属校验）
     * @param reason 冲正原因（写进流水 note，如「请假冲正」）
     * @return 冲正明细 {hours, amount, deletedShells=0, keptShells=0}；未结算返 null
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
        // 🔴 按 deduct.accountId 回<b>原账本</b>（不走 link）：学生可能已改绑别的账本，
        //    但退款必须退回当初扣钱那一本，否则钱在两本账之间凭空搬家。
        BizTuitionAccount acc = accountMapper.selectById(deduct.getAccountId());
        BigDecimal hours = nz(deduct.getHoursDelta()).negate();     // 实扣为负 → 返还为正
        // 金额取「原扣课行冻结的派生金额」取负（B2：不按当前时薪重算历史）
        BigDecimal amount = deduct.getAmountPaid() == null
            ? scale2(hours.multiply(acc == null ? BigDecimal.ZERO : nz(acc.getPricePerHour())))
            : nz(deduct.getAmountPaid()).negate();
        if (acc != null) {
            // occur_date = 所冲场次日期（M4：不是「今天」，否则冲正行会跳到台账最后）
            accountService.applyFlow(acc, TuitionAccountService.FLOW_REVERSE,
                hours, s.getId(), reason, s.getSessionDate(), amount);
        }
        s.setSettleStatus("2");
        // 🔄 D10：反馈壳处置整段撤除 —— 冲正只管钱，不再碰反馈域的任何一行

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hours", num(hours));
        m.put("amount", num(amount));
        // 恒 0（M5 兼容位：FE SessionDetailDrawer 读 rev.deletedShells 做提示，批 3 清）
        m.put("deletedShells", 0);
        m.put("keptShells", 0);
        return m;
    }

    // ─────────────────────────── 销假（PRD-018 AC5） ───────────────────────────

    /**
     * 删掉该场次的「'2' 扣课 + '3' 冲正」流水对（销假用），<b>在调用方事务内</b>。
     *
     * <p>🔴 <b>余额缓存无需变动</b>：一对流水的净额恒为零 —— 冲正行按扣课行的 {@code hours_delta}
     * 取负生成（见 {@link #reverseIfSettled}），{@code applyFlow} 当初把余额先减后加已回到原点，
     * 因此把两行一起删掉后 {@code hours_remain} 仍是对的（G5 实测复核）。
     * 🔴 但只删得掉<b>成对</b>的：只有 '2' 没有 '3'（已结未冲）时删掉会凭空还钱 → 本方法拒绝，
     * 由调用方保证只在「已冲正 / 未结算」的场次上调。
     *
     * <p>删除即释放 uk(session_id, flow_type)，该场可正常重新结算（不撞幂等键）。
     *
     * @return 实删行数（0 = 该场本来就没有流水，纯请假未结算）
     */
    public int dropSessionFlows(Long sessionId) {
        List<BizTuitionFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getSessionId, sessionId)
            .in(BizTuitionFlow::getFlowType,
                List.of(TuitionAccountService.FLOW_DEDUCT, TuitionAccountService.FLOW_REVERSE)));
        if (flows.isEmpty()) {
            return 0;
        }
        boolean hasDeduct = flows.stream().anyMatch(f -> TuitionAccountService.FLOW_DEDUCT.equals(f.getFlowType()));
        boolean hasReverse = flows.stream().anyMatch(f -> TuitionAccountService.FLOW_REVERSE.equals(f.getFlowType()));
        if (hasDeduct && !hasReverse) {
            throw new ServiceException("该场次已结算未冲正，不能销假（请先请假/取消触发冲正）", 400);
        }
        int n = 0;
        for (BizTuitionFlow f : flows) {
            flowMapper.deleteById(f.getId());
            n++;
        }
        return n;
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
        BizTuitionAccount acc = accountMapper.selectById(f.getAccountId());
        BigDecimal hours = nz(f.getHoursDelta()).negate();
        BigDecimal amount = f.getAmountPaid() == null
            ? scale2(hours.multiply(acc == null ? BigDecimal.ZERO : nz(acc.getPricePerHour())))
            : nz(f.getAmountPaid()).negate();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hours", num(hours));
        m.put("amount", num(amount));
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
