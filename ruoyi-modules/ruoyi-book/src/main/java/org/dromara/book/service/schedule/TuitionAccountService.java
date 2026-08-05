package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.TuitionAccountBo;
import org.dromara.book.domain.bo.TuitionFlowBo;
import org.dromara.book.domain.bo.TuitionTransferBo;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.domain.entity.BizStudentAccountLink;
import org.dromara.book.domain.entity.BizTuitionAccount;
import org.dromara.book.domain.entity.BizTuitionFlow;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizStudentAccountLinkMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.mapper.BizTuitionAccountMapper;
import org.dromara.book.mapper.BizTuitionFlowMapper;
import org.dromara.book.util.EduTermUtil;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课时账本 Service（PRD-015 D2/D3/D16 + <b>PRD-018 v3 换血</b>，/teacher/schedule/account/**）。
 *
 * <p><b>模型（v3 D3）</b>：{@link BizTuitionAccount} = 独立<b>账本</b>（不知道学生，一本账一个时薪）；
 * {@link BizStudentAccountLink} = 「学生 × 学科 → 账本」绑定（n:1，共享账本 = 两条绑定同一本，
 * 每人各自的 hours_per_lesson）。普通学生开户 = 同一事务「建本 + 建绑定」，对老师无感知。
 * 余额可为负（欠费不拦截，老师最高权限）。
 *
 * <p><b>单位（D1 v2.1）</b>：底账只记<b>小时</b>；「节」= 小时 ÷ 绑定的每节时长，展示层换算；
 * <b>金额全派生</b> = 小时 × price_per_hour，只有「实收/冻结」落 flow.amount_paid（M9/拍板 A1+B2）。
 *
 * <p><b>台账实时推导（D2，本卡灵魂）</b> —— 口径逐条写死在 {@link #buildLedgerRows}：
 * <ol>
 *   <li>排序键 = {@code COALESCE(occur_date, DATE(create_time))} 正序（兼容换轨中间态 NULL 行）；</li>
 *   <li>🔴 带 session_id 的行，日期与同日次序<b>以 session.session_date + start_time 为准</b>
 *       （单一事实源 = 场次，改期后台账自动跟随，M4 甲案）；</li>
 *   <li>同日：手工行（充值/调整）排<b>当日最前</b>（先交钱后上课，M7）→ 同日多手工行按 id 升序；
 *       同场次 '2' 扣课在前、'3' 冲正在后（flow_type 码序）；</li>
 *   <li>剩余 = 该账本<b>全量流水从头累加</b>，分页/区间只在累加之后切片，区间/分页带
 *       {@code openingBalance} 期初（M10）；台账默认落到<b>最后一页</b>（页内正序，能到最新行，L1）。</li>
 * </ol>
 *
 * <p><b>归属</b>：account.create_by = 登录老师，查/改/流水/导出一律 owner 过滤，防水平越权。
 *
 * <p>🔴 <b>过渡期契约兼容（M5）</b>：VO 继续回吐 {@code studentId/subject/lessonPrice/amountRemain/
 * hoursAfter/amountAfter/amountDelta} 等旧字段的<b>派生值</b>，让旧 FE/H5/机器人在批 3/4 前不断粮；
 * flow BO 带旧 {@code amountDelta} 静默忽略不报错。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class TuitionAccountService {

    /** 流水类型码 */
    public static final String FLOW_RECHARGE = "1";
    public static final String FLOW_DEDUCT = "2";
    public static final String FLOW_REVERSE = "3";
    public static final String FLOW_ADJUST = "4";

    /** 账本状态码：'0' 正常 / '1' 停用（停用 = 不再参与建计划学科下拉与结算取账本）。 */
    public static final String STATUS_ACTIVE = "0";
    public static final String STATUS_DISABLED = "1";

    /** 每节时长缺省（小时）。 */
    public static final BigDecimal DEFAULT_HOURS_PER_LESSON = new BigDecimal("1.00");

    /**
     * 「未停用」条件（PRD-015 bug 批 BUG-3/A）：status 不是 '1' 就算在用。
     * 🔴 用 isNull().or().ne() 而不是裸 ne——SQL 里 {@code NULL <> '1'} 求值为 NULL（不成立），
     * 存量行若 status 为 NULL 会被整条过滤掉，账本凭空消失。
     */
    public static void notDisabled(LambdaQueryWrapper<BizTuitionAccount> w) {
        w.and(x -> x.isNull(BizTuitionAccount::getStatus).or().ne(BizTuitionAccount::getStatus, STATUS_DISABLED));
    }

    private final BizTuitionAccountMapper accountMapper;
    private final BizTuitionFlowMapper flowMapper;
    private final BizStudentAccountLinkMapper linkMapper;
    private final BizStudentMapper studentMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final ScheduleRenderUtil renderUtil;

    // ─────────────────────────── 绑定查询（对外 helper） ───────────────────────────

    /**
     * 学生 × 学科的绑定行（owner 过滤经账本 create_by）；未绑定或无权返 null。
     * 🔴 结算取账本、取缺省扣时的唯一入口（v3 后账本表已无 student_id/subject 列）。
     */
    public BizStudentAccountLink findLink(Long studentId, String subject) {
        if (studentId == null || subject == null || subject.isBlank()) {
            return null;
        }
        BizStudentAccountLink l = linkMapper.selectOne(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getStudentId, studentId)
            .eq(BizStudentAccountLink::getSubject, subject)
            .last("LIMIT 1"));
        if (l == null) {
            return null;
        }
        BizTuitionAccount a = accountMapper.selectById(l.getAccountId());
        if (a == null || !ownedBy(a)) {
            return null;
        }
        return l;
    }

    /** 学生 × 学科绑定到的账本（含停用；无绑定/无权返 null）。 */
    public BizTuitionAccount findAccountOf(Long studentId, String subject) {
        BizStudentAccountLink l = findLink(studentId, subject);
        return l == null ? null : accountMapper.selectById(l.getAccountId());
    }

    /** 该绑定的每节时长（缺省 1.00，永不返 0/负 —— 缺省扣时与折节分母都靠它）。 */
    public BigDecimal hoursPerLessonOf(BizStudentAccountLink link) {
        BigDecimal v = link == null ? null : link.getHoursPerLesson();
        return v == null || v.signum() <= 0 ? DEFAULT_HOURS_PER_LESSON : scale2(v);
    }

    // ─────────────────────────── 账本 CRUD ───────────────────────────

    /** 某学生的全部学科账本（走绑定表反查，owner 过滤，按学科码升序）。 */
    public List<Map<String, Object>> listAccounts(Long studentId) {
        if (studentId == null) {
            throw new ServiceException("请指定学生（studentId 必填）", 400);
        }
        List<BizStudentAccountLink> links = linkMapper.selectList(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getStudentId, studentId)
            .orderByAsc(BizStudentAccountLink::getSubject));
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizStudentAccountLink l : links) {
            BizTuitionAccount a = accountMapper.selectById(l.getAccountId());
            if (a == null || !ownedBy(a)) continue;
            out.add(accountVo(a, l));
        }
        return out;
    }

    /**
     * 我的全部账本（M6 必做入口）：按 create_by 列本人所有账本，<b>含零绑定账本</b> ——
     * 改绑后旧本的冲正退款永远查得到、不会变成任何页面都看不到的孤儿。
     * 每本带绑定的学生名列表（共享本会有多个）。
     */
    public List<Map<String, Object>> listMyAccounts() {
        List<BizTuitionAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<BizTuitionAccount>()
            .eq(BizTuitionAccount::getCreateBy, LoginHelper.getUserId())
            .orderByAsc(BizTuitionAccount::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizTuitionAccount a : accounts) {
            out.add(accountBookVo(a));
        }
        return out;
    }

    /**
     * 开户 / 改绑 / 改本（v3 D3）：<b>建账本 + 建绑定同一事务</b>，普通学生无感知。
     *
     * <p>冲突语义按<b>绑定</b>判：uk(student_id, subject) 命中 = 改该绑定的每节时长 + 该账本时薪
     * （不报错、不动余额）。传 {@code accountId} = 绑到已有账本（换本 / 共享账本）。
     *
     * <p>🔴 改绑守卫（M6-3）：改绑后原账本零绑定且 hours_remain≠0 → 400，
     * 免得余额留在一本没人看得见的账里。
     *
     * @return 账本 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upsertAccount(TuitionAccountBo bo) {
        if (bo == null || bo.getStudentId() == null) {
            throw new ServiceException("请选择学生（studentId 必填）", 400);
        }
        String subject = EduTermUtil.normalizeSubject(bo.getSubject());
        if (subject == null || subject.isBlank()) {
            throw new ServiceException("请选择学科（subject 必填）", 400);
        }
        BizStudent stu = studentMapper.selectById(bo.getStudentId());
        if (stu == null) {
            throw new ServiceException("学生不存在：" + bo.getStudentId(), 400);
        }
        BigDecimal hpl = bo.getHoursPerLesson() == null ? DEFAULT_HOURS_PER_LESSON : scale2(bo.getHoursPerLesson());
        if (hpl.signum() <= 0) {
            throw new ServiceException("每节时长必须大于 0（小时）", 400);
        }
        BigDecimal price = resolvePricePerHour(bo, hpl);
        if (price.signum() < 0) {
            throw new ServiceException("课时单价不能为负", 400);
        }

        // uk(student_id, subject) 是全局唯一（不含 create_by），先按 uk 查再判归属，避免撞唯一索引抛裸 500
        BizStudentAccountLink link = linkMapper.selectOne(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getStudentId, bo.getStudentId())
            .eq(BizStudentAccountLink::getSubject, subject)
            .last("LIMIT 1"));
        if (link != null) {
            BizTuitionAccount cur = requireOwnedAccount(link.getAccountId());
            Long target = bo.getAccountId();
            if (target != null && !target.equals(link.getAccountId())) {
                // 改绑：先守卫原账本，再改指向
                requireOwnedAccount(target);
                guardLeavingAccount(cur, link.getId());
                link.setAccountId(target);
                cur = requireOwnedAccount(target);
            }
            link.setHoursPerLesson(hpl);
            linkMapper.updateById(link);
            applyBookFields(cur, bo, price);
            accountMapper.updateById(cur);
            return cur.getId();
        }

        BizTuitionAccount acc;
        if (bo.getAccountId() != null) {
            // 绑到已有账本（共享账本 / 换本入口）：不新建本，只建绑定
            acc = requireOwnedAccount(bo.getAccountId());
            if (bo.getPricePerHour() != null || bo.getLessonPrice() != null || bo.getName() != null
                || bo.getNote() != null) {
                applyBookFields(acc, bo, price);
                accountMapper.updateById(acc);
            }
        } else {
            acc = new BizTuitionAccount();
            acc.setName(bo.getName());
            acc.setPricePerHour(price);
            acc.setHoursRemain(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            acc.setStatus(STATUS_ACTIVE);
            acc.setNote(bo.getNote());
            acc.setCreateBy(LoginHelper.getUserId());
            accountMapper.insert(acc);
        }
        BizStudentAccountLink l = new BizStudentAccountLink();
        l.setStudentId(bo.getStudentId());
        l.setSubject(subject);
        l.setAccountId(acc.getId());
        l.setHoursPerLesson(hpl);
        l.setCreateBy(LoginHelper.getUserId());
        linkMapper.insert(l);
        return acc.getId();
    }

    /** 时薪口径：pricePerHour 优先；只给旧 lessonPrice（元/节）时按每节时长折算（M5 兼容）。 */
    private BigDecimal resolvePricePerHour(TuitionAccountBo bo, BigDecimal hoursPerLesson) {
        if (bo.getPricePerHour() != null) {
            return scale4(bo.getPricePerHour());
        }
        if (bo.getLessonPrice() != null) {
            return scale4(bo.getLessonPrice().divide(hoursPerLesson, 6, RoundingMode.HALF_UP));
        }
        return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    private void applyBookFields(BizTuitionAccount acc, TuitionAccountBo bo, BigDecimal price) {
        if (bo.getPricePerHour() != null || bo.getLessonPrice() != null) {
            acc.setPricePerHour(price);
        }
        if (bo.getName() != null) {
            acc.setName(bo.getName());
        }
        if (bo.getNote() != null) {
            acc.setNote(bo.getNote());
        }
    }

    /** 改绑/解绑守卫（M6-3）：离开后原账本零绑定且余额不为 0 → 阻断。 */
    private void guardLeavingAccount(BizTuitionAccount acc, Long leavingLinkId) {
        long left = linkMapper.selectCount(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getAccountId, acc.getId())
            .ne(leavingLinkId != null, BizStudentAccountLink::getId, leavingLinkId));
        if (left == 0 && nz(acc.getHoursRemain()).signum() != 0) {
            throw new ServiceException("原账本还剩 " + plain(nz(acc.getHoursRemain()))
                + " 小时且改绑后就没有学生了；请先把余额转到新账本（账本转移）再改绑", 400);
        }
    }

    // 🗑️ PRD-018 批4 删死代码 hasAccount(studentId, subject)：唯一调用方
    // CoursePlanService.requireSubjectAccount 已随批2 D10 摘除建计划开户硬闸一并删除，
    // 批2/批3 两轮复核确认零调用方。需要「是否绑本」判断走 findLink(...) != null。

    /**
     * 停用 / 启用账本（bug 批 BUG-3/A）。停用 = 该学科不再出现在建计划下拉、结算取不到账本；
     * 余额与流水<b>原样保留</b>（停用不是删除，账不能凭空消失），随时可启用回来。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long accountId, String status) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        String st = status == null ? "" : status.trim();
        if (!STATUS_ACTIVE.equals(st) && !STATUS_DISABLED.equals(st)) {
            throw new ServiceException("状态只支持 '0' 正常 / '1' 停用", 400);
        }
        acc.setStatus(st);
        accountMapper.updateById(acc);
    }

    /**
     * 删本（硬删，bug 批 BUG-3/A 拍板口径）：<b>仅零流水账本可删</b>，连带删掉它的绑定行。
     * 🔴 有任何一条流水就只能停用——扣费/冲正只经流水行产生是审计线铁律（D1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long accountId) {
        requireOwnedAccount(accountId);
        long flows = flowMapper.selectCount(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getAccountId, accountId));
        if (flows > 0) {
            throw new ServiceException("该账本已有 " + flows + " 条课时记录，不能删除；如不再使用请改为「停用」", 400);
        }
        linkMapper.delete(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getAccountId, accountId));
        accountMapper.deleteById(accountId);
    }

    // ─────────────────────────── 手工流水（充值 / 调整） ───────────────────────────

    /**
     * 手工流水：充值('1') / 调整('4')。三档输入任给其一（小时 / 节 / 金额，D9），
     * 服务端换算成<b>小时</b>落库；occur_date 由入参给（默认今天，补录可回填历史）；
     * amount_paid = 实收金额原样落库（M9）。
     * 🔴 '2' 扣课 / '3' 冲正 只能由结算链产生（幂等键 uk(session_id,flow_type) 守门），此处拒收。
     *
     * @return 流水 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addFlow(Long accountId, TuitionFlowBo bo) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        String type = bo == null || bo.getFlowType() == null ? "" : bo.getFlowType().trim();
        if (!FLOW_RECHARGE.equals(type) && !FLOW_ADJUST.equals(type)) {
            throw new ServiceException("手工流水只支持 '1' 充值 / '4' 调整（扣课/冲正由结算链产生）", 400);
        }
        BigDecimal hours = resolveHours(acc, bo);
        if (hours.signum() == 0) {
            throw new ServiceException("请填写小时 / 节数 / 金额（三选一，且不能为 0）", 400);
        }
        LocalDate occur = parseDate(bo.getOccurDate());
        BigDecimal paid = bo.getAmountPaid() != null ? scale2(bo.getAmountPaid())
            : (bo.getAmount() != null ? scale2(bo.getAmount()) : null);
        BizTuitionFlow f = applyFlow(acc, type, hours, null, bo.getNote(), occur, paid);
        return f.getId();
    }

    /**
     * 三档输入 → 小时（D9）。优先级：hours（含旧 hoursDelta）→ lessons → amount。
     * 🔴 lessons 档需要「每节时长」：单绑账本取该绑定；共享账本（绑定数&gt;1）基准不唯一，明确拒绝。
     */
    private BigDecimal resolveHours(BizTuitionAccount acc, TuitionFlowBo bo) {
        BigDecimal h = bo.getHours() != null ? bo.getHours() : bo.getHoursDelta();
        if (h != null) {
            return scale2(h);
        }
        if (bo.getLessons() != null) {
            List<BizStudentAccountLink> links = linksOf(acc.getId());
            if (links.size() != 1) {
                throw new ServiceException(links.isEmpty()
                    ? "该账本还没有绑定学生，无法按「节」换算，请改用小时或金额"
                    : "共享账本每人每节时长不同，无法按「节」换算，请改用小时或金额", 400);
            }
            return scale2(bo.getLessons().multiply(hoursPerLessonOf(links.get(0))));
        }
        if (bo.getAmount() != null) {
            BigDecimal price = nz(acc.getPricePerHour());
            if (price.signum() <= 0) {
                throw new ServiceException("该账本还没有设置课时单价，无法按金额换算", 400);
            }
            return scale2(bo.getAmount().divide(price, 6, RoundingMode.HALF_UP));
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 记一笔流水并同步账本 hours_remain 缓存（<b>唯一的余额写入口</b>，禁止别处裸 UPDATE 余额列）。
     * 扣课/冲正走结算链时也调本方法，故此处不限制类型。
     *
     * <p>🔄 v3：不再写 amount_delta / hours_after / amount_after（金额派生 + 台账实时推导）。
     *
     * @param acc        账本（调用方已做归属校验）
     * @param flowType   '1' 充值 / '2' 扣课 / '3' 冲正 / '4' 调整
     * @param hours      小时增量（扣为负）
     * @param sessionId  关联场次（扣课/冲正必填=幂等键；手工流水传 null）
     * @param note       备注
     * @param occurDate  业务日期（null = 今天）
     * @param amountPaid 实收/冻结金额（可空；符号与 hours 同向）
     * @return 落库后的流水行
     */
    @Transactional(rollbackFor = Exception.class)
    public BizTuitionFlow applyFlow(BizTuitionAccount acc, String flowType, BigDecimal hours,
                                    Long sessionId, String note, LocalDate occurDate, BigDecimal amountPaid) {
        BigDecimal h = scale2(hours);
        BizTuitionFlow f = new BizTuitionFlow();
        f.setAccountId(acc.getId());
        f.setFlowType(flowType);
        f.setOccurDate(occurDate == null ? LocalDate.now() : occurDate);
        f.setHoursDelta(h);
        f.setAmountPaid(amountPaid == null ? null : scale2(amountPaid));
        f.setSessionId(sessionId);
        f.setNote(note);
        f.setCreateBy(LoginHelper.getUserId());
        flowMapper.insert(f);

        // hours_remain = 纯缓存（可由流水全量重算）；台账「剩余」列走实时推导，不再吃这里的快照
        acc.setHoursRemain(scale2(nz(acc.getHoursRemain()).add(h)));
        accountMapper.updateById(acc);
        return f;
    }

    /**
     * 账本间转账（M6-2）：一个事务产一对 '4' 调整行（转出为负 / 转入为正）并互写 rel_flow_id，
     * 让换本 / 拆本在台账上是<b>一笔可解释的动作</b>。
     *
     * @return {fromFlowId, toFlowId, hours}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(TuitionTransferBo bo) {
        if (bo == null || bo.getFromAccountId() == null || bo.getToAccountId() == null) {
            throw new ServiceException("请选择转出与转入账本（fromAccountId / toAccountId 必填）", 400);
        }
        if (bo.getFromAccountId().equals(bo.getToAccountId())) {
            throw new ServiceException("转出与转入不能是同一本账", 400);
        }
        BigDecimal hours = scale2(bo.getHours());
        if (hours.signum() <= 0) {
            throw new ServiceException("转移小时数需大于 0", 400);
        }
        BizTuitionAccount from = requireOwnedAccount(bo.getFromAccountId());
        BizTuitionAccount to = requireOwnedAccount(bo.getToAccountId());
        LocalDate occur = parseDate(bo.getOccurDate());
        String note = bo.getNote() == null || bo.getNote().isBlank() ? "账本转移" : bo.getNote().trim();

        BizTuitionFlow out = applyFlow(from, FLOW_ADJUST, hours.negate(), null,
            note + "（转出至 " + bookLabel(to) + "）", occur, null);
        BizTuitionFlow in = applyFlow(to, FLOW_ADJUST, hours, null,
            note + "（转入自 " + bookLabel(from) + "）", occur, null);
        out.setRelFlowId(in.getId());
        in.setRelFlowId(out.getId());
        flowMapper.updateById(out);
        flowMapper.updateById(in);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fromFlowId", String.valueOf(out.getId()));
        m.put("toFlowId", String.valueOf(in.getId()));
        m.put("hours", num(hours));
        return m;
    }

    /** 账本可读标签：name → 绑定学生名 → 「账本 #id」。 */
    private String bookLabel(BizTuitionAccount a) {
        if (a.getName() != null && !a.getName().isBlank()) {
            return a.getName();
        }
        String names = String.join("+", studentNamesOf(a.getId()));
        return names.isBlank() ? "账本 #" + a.getId() : names;
    }

    // ─────────────────────────── 台账（实时推导） ───────────────────────────

    /**
     * 消耗台账（AC1/AC4/D2）：全量流水按业务日期正序累加出<b>逐行剩余</b>，再做区间/分页切片。
     * 🔴 默认落到<b>最后一页</b>（最近的行），页内正序 —— 正序 + 第 1 页会让老师首屏永远是最旧记录（L1）。
     *
     * @param mode 切片口径（D13）：{@code cycle} = 最近重置周期；其余/不传 = 全量。
     *             🔴 显式给了 startDate/endDate 时 mode 失效走区间。<b>页面浏览默认仍是全量</b>
     *             （只有导出默认吃 cycle），别在这里改默认值。
     * @return {rows, total, pageNum, pageSize, pages, openingBalance, openingAmount, mode, cycleStart, shared, ...}
     */
    public Map<String, Object> ledger(Long accountId, String startDate, String endDate, String mode,
                                      Integer pageNum, Integer pageSize) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        List<Map<String, Object>> all = buildLedgerRows(acc);
        LedgerSlice sl = sliceRows(all, startDate, endDate, mode);
        List<Map<String, Object>> filtered = sl.rows;

        int ps = pageSize == null || pageSize < 1 ? 100 : pageSize;
        int pages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ps));
        // pageNum 缺省 = 最后一页（最近的行）；显式给了就按给的走
        int pn = pageNum == null || pageNum < 1 ? pages : Math.min(pageNum, pages);
        int fromIdx = Math.min((pn - 1) * ps, filtered.size());
        int toIdx = Math.min(fromIdx + ps, filtered.size());
        List<Map<String, Object>> slice = new ArrayList<>(filtered.subList(fromIdx, toIdx));

        // 期初 = 本页第一行之前的累计（含被区间过滤掉的行）
        BigDecimal openingHours = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal openingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (!slice.isEmpty()) {
            Object firstId = slice.get(0).get("id");
            for (Map<String, Object> row : all) {
                if (String.valueOf(firstId).equals(String.valueOf(row.get("id")))) break;
                openingHours = scale2(openingHours.add(dec(row.get("hoursDelta"))));
                openingAmount = scale2(openingAmount.add(dec(row.get("amount"))));
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", slice);
        r.put("total", filtered.size());
        r.put("pageNum", pn);
        r.put("pageSize", ps);
        r.put("pages", pages);
        r.put("openingBalance", num(openingHours));
        r.put("openingAmount", num(openingAmount));
        // D13 additive：本次生效的切片口径 + 本周期起始日（FE 用来出「本周期」筛选态与期初行）
        r.put("mode", sl.mode);
        r.put("cycleStart", sl.cycleStart);
        r.put("account", accountBookVo(acc));
        r.put("shared", linksOf(acc.getId()).size() > 1);
        return r;
    }

    /** 切片结果（D13）：行 + 期初 + 生效口径，ledger 与 exportLedgerPng 共用同一把刀。 */
    private static final class LedgerSlice {
        List<Map<String, Object>> rows;
        BigDecimal openingHours = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal openingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        /** 生效口径：all / cycle / range */
        String mode = "all";
        /** 本周期起始日（mode=cycle 且找到充值行时才有值） */
        String cycleStart;
        /** 切片是否一直取到台账末行（决定导出 meta 写「当前剩余」还是「期末剩余」） */
        boolean toEnd = true;
        /** 是否从中间切起（决定要不要渲期初行） */
        boolean sliced;
    }

    /**
     * 台账切片（<b>D13 唯一实现处</b>）：全量行 → 最近重置周期 / 日期区间 / 全量。
     *
     * <p>🔴 <b>重置周期</b> = 展示排序后的<b>最后一笔充值行</b>（{@link #FLOW_RECHARGE}）起（含该行）到末行。
     * 定位必须在 {@link #buildLedgerRows} 排完序之后倒着找 —— <b>不是按 create_time</b>：
     * 补录的历史充值 create_time 最新、业务日期最旧，按写入时间找会把周期切到几个月前那一笔上。
     *
     * <p>🔴 显式给了 startDate/endDate → 区间优先，mode 失效（老师明确指定的时间范围压过默认口径）。
     * 🔴 账本一笔充值都没有（纯调整/纯扣课）→ 退化为全量，绝不返空表。
     *
     * <p>剩余值不受切片影响：每行的 hoursAfter 在全量累加阶段就算完了（M10），
     * 切片只决定「展示哪几行 + 期初是多少」。
     */
    private LedgerSlice sliceRows(List<Map<String, Object>> all, String startDate, String endDate, String mode) {
        LedgerSlice sl = new LedgerSlice();
        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);

        if (from == null && to == null && "cycle".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            int idx = -1;
            for (int i = all.size() - 1; i >= 0; i--) {
                if (FLOW_RECHARGE.equals(String.valueOf(all.get(i).get("flowType")))) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                sl.mode = "cycle";
                sl.cycleStart = str(all.get(idx).get("date"));
                sl.rows = new ArrayList<>(all.subList(idx, all.size()));
                sl.sliced = idx > 0;
                accumulateOpening(sl, all, idx);
                return sl;
            }
            // 一笔充值都没有 → 全量（mode 保持 "all"，FE 据此知道「本周期」筛不出东西）
            sl.rows = new ArrayList<>(all);
            return sl;
        }

        if (from == null && to == null) {
            sl.rows = new ArrayList<>(all);
            return sl;
        }

        sl.mode = "range";
        List<Map<String, Object>> filtered = new ArrayList<>();
        int firstIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            LocalDate d = parseDate((String) all.get(i).get("date"));
            if (from != null && d != null && d.isBefore(from)) continue;
            if (to != null && d != null && d.isAfter(to)) continue;
            if (firstIdx < 0) firstIdx = i;
            filtered.add(all.get(i));
        }
        sl.rows = filtered;
        sl.sliced = firstIdx > 0;
        sl.toEnd = !filtered.isEmpty()
            && String.valueOf(filtered.get(filtered.size() - 1).get("id"))
            .equals(String.valueOf(all.get(all.size() - 1).get("id")));
        accumulateOpening(sl, all, firstIdx < 0 ? all.size() : firstIdx);
        return sl;
    }

    /** 期初 = 切片首行之前的全部行累计（含被区间过滤掉的行，M10）。 */
    private void accumulateOpening(LedgerSlice sl, List<Map<String, Object>> all, int firstIdx) {
        for (int i = 0; i < firstIdx && i < all.size(); i++) {
            sl.openingHours = scale2(sl.openingHours.add(dec(all.get(i).get("hoursDelta"))));
            sl.openingAmount = scale2(sl.openingAmount.add(dec(all.get(i).get("amount"))));
        }
    }

    /**
     * 台账全量行（正序 + 逐行推导剩余）—— <b>PRD-018 §4 台账推导口径的唯一实现处</b>。
     * ledger / exportLedgerPng 共用，保证屏上与导出单逐行一致（AC8）。
     */
    private List<Map<String, Object>> buildLedgerRows(BizTuitionAccount acc) {
        List<BizTuitionFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getAccountId, acc.getId()));
        List<BizStudentAccountLink> links = linksOf(acc.getId());
        boolean shared = links.size() > 1;
        BigDecimal price = nz(acc.getPricePerHour());

        List<Object[]> keyed = new ArrayList<>();
        for (BizTuitionFlow f : flows) {
            BizScheduleSession s = f.getSessionId() == null ? null : sessionMapper.selectById(f.getSessionId());
            // ① 日期：带 session 的行以场次日期为准（单一事实源，改期自动跟随，M4 甲案）；
            //    否则 COALESCE(occur_date, DATE(create_time))（换轨中间态兜底，M2）
            String date = null;
            if (s != null && s.getSessionDate() != null) {
                date = s.getSessionDate().toString();
            } else if (f.getOccurDate() != null) {
                date = f.getOccurDate().toString();
            } else {
                date = localDate(f.getCreateTime());
            }
            // ② 同日次序：手工行(0) 排当日最前（先交钱后上课，M7）；场次行(1) 按 start_time
            // 🔴 P3：场次行按 flow_type 判（'2' 扣课 / '3' 冲正 = 场次行），不按「selectById 查得到」判——
            //    场次被硬删（DELETE /session/{id} 是物理删）后 s==null，按旧口径这行会被当成手工行
            //    窜到当日最前，台账行序错乱。流水类型是不会被删的事实。
            boolean sessionRow = FLOW_DEDUCT.equals(f.getFlowType()) || FLOW_REVERSE.equals(f.getFlowType());
            int group = sessionRow ? 1 : 0;
            String time = s == null ? "" : nvl(trimSec(s.getStartTime()));
            keyed.add(new Object[]{nvl(date), group, time, nvl(f.getFlowType()), f.getId() == null ? 0L : f.getId(), f, s});
        }
        keyed.sort(Comparator
            .comparing((Object[] k) -> (String) k[0])                 // 业务日期
            .thenComparingInt(k -> (Integer) k[1])                    // 手工行在当日最前
            .thenComparing(k -> (String) k[2])                        // 场次起始时间
            .thenComparingLong(k -> (Long) k[4])                      // 同日多条手工行按 id 升序（§4；同场次 '2' 先插 '3' 后插，id 天然使 '2' 在前）
            .thenComparing(k -> (String) k[3]));                      // flowType 纯兜底

        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal runHours = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal runAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Object[] k : keyed) {
            BizTuitionFlow f = (BizTuitionFlow) k[5];
            BizScheduleSession s = (BizScheduleSession) k[6];
            BigDecimal h = nz(f.getHoursDelta());
            // 金额：实收/冻结优先（M9/B2），否则按当前时薪派生
            BigDecimal amount = f.getAmountPaid() != null ? scale2(f.getAmountPaid()) : scale2(h.multiply(price));
            runHours = scale2(runHours.add(h));
            runAmount = scale2(runAmount.add(amount));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId() == null ? null : String.valueOf(f.getId()));
            m.put("date", k[0]);
            m.put("timeRange", s == null ? null : timeRange(s));
            m.put("content", contentOf(f, s));
            m.put("flowType", f.getFlowType());
            m.put("hoursDelta", num(h));
            m.put("amount", num(amount));
            m.put("amountPaid", num(f.getAmountPaid()));
            m.put("hoursAfter", num(runHours));       // 🔄 推导值（旧字段名保留，M5 过渡兼容）
            m.put("amountAfter", num(runAmount));     // 🔄 逐行派生后求和（AC9 口径）
            m.put("amountDelta", num(amount));        // 🔄 旧契约兼容：等于本行金额
            m.put("sessionId", f.getSessionId() == null ? null : String.valueOf(f.getSessionId()));
            m.put("relFlowId", f.getRelFlowId() == null ? null : String.valueOf(f.getRelFlowId()));
            // 规则①：共享账本（绑定数>1）流水行显示学生名；手工行无归属 → null（展示层兜底「—」）
            m.put("studentName", shared && s != null ? studentName(s.getTargetId()) : null);
            out.add(m);
        }
        return out;
    }

    /** 场次被硬删后，其扣课/冲正流水行的内容列兜底（P3；家长可见物，不写内部词）。 */
    private static final String SESSION_GONE = "（场次已删除）";

    /**
     * 台账「内容」列。
     *
     * <p>🔴 <b>取值链（PRD-018 D5/G7）</b>：{@code session.content}（这节实际讲了什么）
     * → 课次标题 {@code sessionTitle} → 「正课」兜底；手工行（充值/调整）取流水备注。
     * 冲正行在内容后挂原因（如「一元一次方程（请假冲正）」）。
     *
     * <p>P3：场次被硬删（{@code DELETE /session/{id}} 物理删）时 s==null，但流水行还在 ——
     * 扣课/冲正行给「{@value #SESSION_GONE}」兜底，别掉成空白列或串成手工行备注。
     */
    private String contentOf(BizTuitionFlow f, BizScheduleSession s) {
        boolean sessionRow = FLOW_DEDUCT.equals(f.getFlowType()) || FLOW_REVERSE.equals(f.getFlowType());
        if (s == null) {
            if (!sessionRow) {
                return f.getNote();
            }
            return (f.getNote() == null || f.getNote().isBlank())
                ? SESSION_GONE : SESSION_GONE + "（" + f.getNote() + "）";
        }
        // content 优先：老师结算时记的「这节讲了什么」比课次标题更贴实际
        String title = s.getContent() == null || s.getContent().isBlank() ? sessionTitle(s) : s.getContent().trim();
        if (FLOW_REVERSE.equals(f.getFlowType())) {
            return (f.getNote() == null || f.getNote().isBlank()) ? title : title + "（" + f.getNote() + "）";
        }
        return (title == null || title.isBlank()) ? f.getNote() : title;
    }

    // ─────────────────────────── 流水单导出 PNG（D16） ───────────────────────────

    /**
     * 课时流水单 PNG（D16 / AC14）：标题栏 +（时薪 / 截至 / 当前剩余）meta 行 + 网格表<b>升序</b>
     * （日期 / 上课时间 /[学生]/ 内容 / 时长变动 / 剩余），充值行绿、冲正行红，🔴 无合计行。
     * 🔴 家长可见物：零内部词。行序与 {@link #ledger} 同源（buildLedgerRows），保证逐行一致（AC8）。
     *
     * <p>🔄 <b>PRD-018 批3 版式跟随（稿1 FP-9「只换图内版式：补双单位，共享账本自动带学生列」）</b>：
     * ①单位词全线换轨 —— 表头「课时变动/剩余课时」→「时长变动/剩余」，数值一律带「小时」，
     * 单绑账本再挂副显「（N 节）」（D1 v2.1 双单位）；共享账本每人每节时长不同 → <b>只按小时不折节</b>（D4 规则②）。
     * ②共享账本单独出「学生」列（原来是挤在内容列前的 "名 · 内容" 前缀，家长看不清哪列是人）。
     *
     * <p>🔄 <b>PRD-018 批6 D13（导出口径）</b>：<b>默认导出「最近重置周期」</b>——从最后一笔充值起到现在的
     * 消耗，正是家长要看的「这期钱用到哪儿了」（E8 手工单就是这个形态）。显式给 startDate/endDate
     * 走区间；切片后首行前渲期初行；账本一笔充值都没有则退化全量。
     * <p>🔄 <b>批6 D14（内容分条）</b>：内容单元格按「｜」/「N.」拆条分行、「XX：」前缀加粗，
     * 行高按<b>条数</b>估（挤成一坨的长串以前是一行 40 字裹成三行，现在是三条各一行）。
     *
     * @param mode 不传/空 = {@code cycle}（本周期）；传 {@code all} 导全量
     */
    public Map<String, Object> exportLedgerPng(Long accountId, String startDate, String endDate, String mode) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        List<BizStudentAccountLink> links = linksOf(accountId);
        String title = String.join("+", studentNamesOf(accountId));
        if (title.isBlank()) {
            title = acc.getName() == null || acc.getName().isBlank() ? "课时账本" : acc.getName();
        }
        String subjectLabel = links.size() == 1 ? EduTermUtil.subjectLabel(links.get(0).getSubject()) : null;

        List<Map<String, Object>> all = buildLedgerRows(acc);
        // 🔴 导出默认吃 cycle（与页面浏览默认全量相反，D13）：null/空 → cycle
        String m0 = mode == null || mode.isBlank() ? "cycle" : mode.trim();
        LedgerSlice sl = sliceRows(all, startDate, endDate, m0);
        List<Map<String, Object>> rows = sl.rows;

        String html = buildLedgerHtml(title, subjectLabel, acc, rows, sl);
        // 高度收边（同 FeedbackSheetService BUG-014 口径）：标题条+meta 行+表头+内边距 ≈ 124px，每行 27px。
        // 🔴 内容列每行字数随列数变：批3 加了「学生」列（共享本）与更宽的双单位列，内容列被挤窄 →
        //    仍按 30 字/行估会低估行数、把长内容裁掉。宁可多算（底部留点白），绝不少算。
        // 🔴 D14：内容拆条后每条独占一行 —— 行高必须按「逐条各自折行数之和」算，
        //    按整串长度估会把三条挤成一条的高度，长内容直接被裁在图外。
        boolean sharedBook = links.size() > 1;
        double perLine = sharedBook ? 18.0 : (links.size() == 1 ? 21.0 : 26.0);
        int rowsH = 0;
        for (Map<String, Object> row : rows) {
            rowsH += rowHeight(contentLines(str(row.get("content")), perLine));
        }
        if (sl.sliced) {
            rowsH += rowHeight(1);    // 期初行
        }
        if (rows.isEmpty()) {
            rowsH += 34;
        }
        int height = 124 + rowsH + 14;
        String file = renderUtil.renderToPng(html, "ledger_" + accountId, 720, height);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("url", "/teacher/schedule/artifact?path=" + file);
        m.put("mode", sl.mode);
        m.put("cycleStart", sl.cycleStart);
        m.put("rows", rows.size());
        return m;
    }

    /** Excel 风格流水单 HTML（openhtmltopdf 不支持 flex/grid → 纯 table 布局）。 */
    private String buildLedgerHtml(String stuName, String subjectLabel, BizTuitionAccount acc,
                                   List<Map<String, Object>> rows, LedgerSlice sl) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>");
        sb.append("*{box-sizing:border-box;margin:0;padding:0}");
        sb.append("body{font-family:'cjk','cjkhei';color:#243330;background:#fff;width:720px;font-size:12.5px}");
        sb.append(".wrap{padding:14px 16px 16px}");
        sb.append(".bar{background:#0f766e;color:#fff;text-align:center;font-family:'cjkhei';")
            .append("font-weight:bold;font-size:16px;padding:10px 8px;border-radius:4px 4px 0 0}");
        sb.append(".meta{width:100%;border:1px solid #d9e2e0;border-top:none;background:#f6faf9}");
        sb.append(".meta td{padding:7px 10px;font-size:12px;color:#3d5551}");
        sb.append(".meta td b{color:#0b5d56}");
        sb.append("table.grid{border-collapse:collapse;width:100%;table-layout:fixed;margin-top:10px}");
        sb.append("table.grid th{background:#e8f2f0;color:#0b5d56;font-family:'cjkhei';font-weight:bold;")
            .append("font-size:12px;padding:7px 8px;text-align:center;border:1px solid #c9dbd7}");
        sb.append("table.grid td{border:1px solid #dbe6e3;padding:6px 8px;font-size:12px;color:#333;")
            .append("vertical-align:middle;word-wrap:break-word}");
        sb.append("table.grid td.c{text-align:center}");
        sb.append("table.grid td.r{text-align:right;font-weight:bold}");
        sb.append("table.grid td .sub{font-size:10.5px;font-weight:normal;color:#7d928d}");
        sb.append("tr.in td{background:#f2fbf5;color:#15803d}");
        sb.append("tr.rev td{background:#fdf2f2;color:#b91c1c}");
        sb.append("tr.op td{background:#f5f8f7;color:#6d7f7b}");
        // D14 内容分条（朴素口径：分行 + 前缀加粗，不做色块——这是家长手上的纸质件）
        sb.append("table.grid td .cl{line-height:1.45;padding:1px 0}");
        sb.append("table.grid td .cl b{font-family:'cjkhei';color:#0b5d56}");
        sb.append("table.grid td .cl i{font-style:normal;color:#7d928d;padding-right:2px}");
        sb.append("</style></head><body><div class=\"wrap\">");

        sb.append("<div class=\"bar\">课时流水单 · ").append(esc(stuName));
        if (subjectLabel != null && !subjectLabel.isBlank()) {
            sb.append("（").append(esc(subjectLabel)).append("）");
        }
        sb.append("</div>");
        // 🔴 P4：「当前剩余」取<b>台账末行的推导值</b>，不再吃 acc.hours_remain 缓存 ——
        //    缓存与逐行累加一旦漂移（历史裸 UPDATE / 手工改库），家长手上的单子会自相矛盾：
        //    最后一行写着 8.5，头部却写 10.5。单一事实源 = 流水本身。
        //    金额同口径取末行 amountAfter（AC9：导出金额 = 逐行派生后求和，不是 余额×时薪 再算一次）。
        //    🔴 D13 切片后取的是<b>切片末行</b>：区间导出报的是「这段结束时还剩多少」，
        //    本周期导出的切片一直取到末行，仍旧等于当前余额。切片为空 → 退回期初值。
        BigDecimal remain = rows.isEmpty() ? sl.openingHours
            : scale2(dec(rows.get(rows.size() - 1).get("hoursAfter")));
        BigDecimal remainAmount = rows.isEmpty() ? sl.openingAmount
            : scale2(dec(rows.get(rows.size() - 1).get("amountAfter")));
        // 折节基准：单绑账本才有唯一的每节时长；共享本（绑定数≠1）传 null = 只按小时（D4 规则②）
        List<BizStudentAccountLink> links = linksOf(acc.getId());
        boolean shared = links.size() > 1;
        BigDecimal perLesson = links.size() == 1 ? hoursPerLessonOf(links.get(0)) : null;

        sb.append("<table class=\"meta\"><tr>");
        // 🔴 D11：对外只有「课时」概念——单价一律「元/节」，禁「时薪/元/小时」。
        //    单绑本 = 每节时长 × 内部计价参数；共享本逐绑定各报各的；零绑定不显示单价格。
        if (!links.isEmpty()) {
            StringBuilder price = new StringBuilder();
            for (BizStudentAccountLink l : links) {
                BigDecimal hpl = hoursPerLessonOf(l);
                BigDecimal perLessonPrice = scale2(hpl.multiply(nz(acc.getPricePerHour())));
                if (price.length() > 0) {
                    price.append("　");
                }
                if (shared) {
                    BizStudent st = studentMapper.selectById(l.getStudentId());
                    price.append(esc(st == null ? "学生" + l.getStudentId() : st.getName())).append(" ");
                }
                price.append("每节 <b>").append(plain(hpl)).append("</b> 小时 · <b>")
                    .append(plain(perLessonPrice)).append("</b> 元/节");
            }
            sb.append("<td>").append(price).append("</td>");
        }
        // D13：统计区间格 —— 本周期 / 指定区间 / 全量（全量沿用原来的「截至 今天」）
        sb.append("<td>").append(rangeLabel(sl)).append("</td>");
        sb.append("<td>").append(sl.toEnd ? "当前剩余" : "期末剩余").append(" <b>")
            .append(plain(remain)).append("</b> 小时 · <b>")
            .append(plain(remainAmount)).append("</b> 元</td>");
        sb.append("</tr></table>");

        sb.append("<table class=\"grid\">");
        sb.append("<tr>")
            .append("<th style=\"width:92px\">日期</th>")
            .append("<th style=\"width:104px\">上课时间</th>");
        if (shared) {
            // 规则①：绑定数>1 才出学生列（纯数据驱动，无开关无特例入口）
            sb.append("<th style=\"width:74px\">学生</th>");
        }
        sb.append("<th>内容</th>")
            .append("<th style=\"width:").append(perLesson != null ? 106 : 88).append("px\">时长变动</th>")
            .append("<th style=\"width:").append(perLesson != null ? 106 : 88).append("px\">剩余</th>")
            .append("</tr>");
        // D13 期初行：从中间切起时必须交代「这一段开始前手上还有多少」，
        //    E8 家长单的头一行就是它（那次是 0：前期课时正好用完才充的新钱）。
        if (sl.sliced) {
            sb.append("<tr class=\"op\"><td class=\"c\">期初</td><td class=\"c\">—</td>");
            if (shared) {
                sb.append("<td class=\"c\"></td>");
            }
            sb.append("<td>上期结余</td><td class=\"r\">—</td>");
            sb.append("<td class=\"r\">").append(dualCell(num(sl.openingHours), perLesson, false)).append("</td>");
            sb.append("</tr>");
        }
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("flowType"));
            String cls = FLOW_RECHARGE.equals(type) ? " class=\"in\"" : (FLOW_REVERSE.equals(type) ? " class=\"rev\"" : "");
            sb.append("<tr").append(cls).append(">");
            sb.append("<td class=\"c\">").append(esc(str(row.get("date")))).append("</td>");
            sb.append("<td class=\"c\">").append(esc(str(row.get("timeRange")))).append("</td>");
            if (shared) {
                // 手工行（充值/调整）不属于某个学生 → 留白，别硬安一个名字
                sb.append("<td class=\"c\">").append(esc(str(row.get("studentName")))).append("</td>");
            }
            sb.append("<td>").append(contentCell(str(row.get("content")))).append("</td>");
            sb.append("<td class=\"r\">").append(dualCell(row.get("hoursDelta"), perLesson, true)).append("</td>");
            sb.append("<td class=\"r\">").append(dualCell(row.get("hoursAfter"), perLesson, false)).append("</td>");
            sb.append("</tr>");
        }
        if (rows.isEmpty()) {
            sb.append("<tr><td class=\"c\" colspan=\"").append(shared ? 6 : 5)
                .append("\" style=\"color:#999;padding:14px\">（暂无记录）</td></tr>");
        }
        // 🔴 D16 明确：无合计行
        sb.append("</table></div></body></html>");
        return sb.toString();
    }

    /**
     * 双单位数值格（D1 v2.1）：「−1.5 小时<span>（1 节）</span>」。
     * {@code perLesson} 为 null（共享账本，基准不唯一）时只出小时，不折节（D4 规则②）。
     */
    private String dualCell(Object hours, BigDecimal perLesson, boolean signed) {
        BigDecimal h = scale2(dec(hours));
        String main = (signed ? signed(hours) : plain(h)) + " 小时";
        if (perLesson == null || perLesson.signum() <= 0) {
            return esc(main);
        }
        BigDecimal lessons = scale2(h.divide(perLesson, 6, RoundingMode.HALF_UP));
        String sub = (signed && lessons.signum() > 0 ? "+" : "") + plain(lessons) + " 节";
        return esc(main) + "<span class=\"sub\">（" + esc(sub) + "）</span>";
    }

    /** meta 行的统计区间格（D13）：本周期 / 指定区间 / 全量。 */
    private String rangeLabel(LedgerSlice sl) {
        if ("cycle".equals(sl.mode)) {
            return "本期 <b>" + esc(nvl(sl.cycleStart)) + "</b> 起至今";
        }
        if ("range".equals(sl.mode)) {
            String a = sl.rows.isEmpty() ? "" : str(sl.rows.get(0).get("date"));
            String b = sl.rows.isEmpty() ? "" : str(sl.rows.get(sl.rows.size() - 1).get("date"));
            // 🔴 用「至」不用「~」：波浪号在 openhtmltopdf 的中文字体里渲成上标小尾巴，家长看着像乱码
            return "区间 <b>" + esc(a) + "</b> 至 <b>" + esc(b) + "</b>";
        }
        return "截至 <b>" + LocalDate.now() + "</b>";
    }

    // ────────────────── 上课内容分条（D14，与 FE utils/lessonContent.ts 同规则） ──────────────────

    /**
     * 内容串的一条（D14）。
     *
     * @param ord   序号形态的条目号（「1.100 以内的加减」的 "1"）；无则 null
     * @param label 「XX：」分类标签（「思维题：大数的计算」的 "思维题"）；无则 null
     * @param text  正文
     */
    private record ContentSeg(String ord, String label, String text) {
    }

    /** 「N.」条目标记：串首 或 前面是空白（含全角空格）才算 —— 防「圆周率 3.14」被当成第 3 条。 */
    private static final java.util.regex.Pattern NUM_ITEM =
        java.util.regex.Pattern.compile("(?:^|(?<=[\\s\\u3000]))(\\d{1,2})\\s*[.．、]\\s*");

    /** 分类标签上限字数（「拓展奥数」4 字；超了多半是句子里正好有个冒号，不是标签）。 */
    private static final int LABEL_MAX = 8;

    /**
     * 上课内容拆条（<b>D14 规则唯一实现处（BE 侧）</b>，与 FE {@code utils/lessonContent.ts} 一一对应）。
     *
     * <p>真实数据两形态：
     * <pre>
     *   ① 思维题：大数的计算及灵活运用｜同步：大数的认识和改写｜拓展奥数：定义新运算、错题回顾
     *   ② 1.100 以内的加减　2.100 以内的退位加减　3.找规律
     * </pre>
     * ①按「｜」拆；②按行首起的「N.」拆（🔴 <b>≥2 条且首个序号在串首</b>才拆 —— 「1.100」里的小数点
     * 若按裸正则切会把「100 以内的加减」腰斩，序号必须紧跟分隔空白才算）。
     * 每条再认「XX：」前缀为分类标签（长 2–8 字、非纯数字；「9:00 上课」的 "9" 不算）。
     * 拆不出来 = 单条原样返回，绝不改字。
     */
    private List<ContentSeg> parseContentSegs(String raw) {
        List<ContentSeg> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        String s = raw.trim();
        if (s.indexOf('｜') >= 0 || s.indexOf('|') >= 0) {
            for (String p : s.split("[｜|]")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    out.add(labelOf(null, t));
                }
            }
            return out.isEmpty() ? List.of(labelOf(null, s)) : out;
        }
        List<ContentSeg> numbered = numberedSegs(s);
        if (!numbered.isEmpty()) {
            return numbered;
        }
        out.add(labelOf(null, s));
        return out;
    }

    /** 「N.」形态拆条；不成立（少于 2 条 / 首个序号不在串首 / 序号不递增）返空表。 */
    private List<ContentSeg> numberedSegs(String s) {
        java.util.regex.Matcher m = NUM_ITEM.matcher(s);
        List<int[]> marks = new ArrayList<>();     // [markStart, textStart, ord]
        while (m.find()) {
            marks.add(new int[]{m.start(), m.end(), Integer.parseInt(m.group(1))});
        }
        if (marks.size() < 2 || marks.get(0)[0] != 0) {
            return List.of();
        }
        for (int i = 1; i < marks.size(); i++) {
            if (marks.get(i)[2] <= marks.get(i - 1)[2]) {
                return List.of();      // 序号不递增 = 多半是小数/时刻被误命中
            }
        }
        List<ContentSeg> out = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : s.length();
            String text = s.substring(marks.get(i)[1], end).trim();
            if (!text.isEmpty()) {
                out.add(labelOf(String.valueOf(marks.get(i)[2]), text));
            }
        }
        return out.size() < 2 ? List.of() : out;
    }

    /** 认「XX：」分类标签（长 2–8 字、非纯数字、后面还有正文才算）。 */
    private ContentSeg labelOf(String ord, String text) {
        int i = text.indexOf('：');
        if (i < 0) {
            i = text.indexOf(':');
        }
        if (i >= 2 && i <= LABEL_MAX && i + 1 < text.length()) {
            String lb = text.substring(0, i).trim();
            String body = text.substring(i + 1).trim();
            if (!lb.isEmpty() && !body.isEmpty() && !lb.matches("\\d+")) {
                return new ContentSeg(ord, lb, body);
            }
        }
        return new ContentSeg(ord, null, text);
    }

    /** 导出单的内容单元格（D14）：一条一行，序号浅色、分类标签加粗。 */
    private String contentCell(String raw) {
        List<ContentSeg> segs = parseContentSegs(raw);
        if (segs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentSeg g : segs) {
            sb.append("<div class=\"cl\">");
            if (g.ord() != null) {
                sb.append("<i>").append(esc(g.ord())).append(".</i>");
            }
            if (g.label() != null) {
                sb.append("<b>").append(esc(g.label())).append("</b> ");
            }
            sb.append(esc(g.text())).append("</div>");
        }
        return sb.toString();
    }

    /**
     * 表格行高估算（px）：单元格上下内边距 + 边框 ≈ 15，每行文字 ≈ 18（12.5px 字 × line-height 1.45）。
     *
     * <p>🔴 批6 修正：原口径「行数 × 27」把<b>单行</b>行高估成 27（实测 ≈ 32），一张单子攒下来
     * 差十几 px，末行底边被裁在图外（本批目检肉眼可见）。拆成「底座 + 每行」两段后，
     * 单行 33 ≥ 32、三行 69 ≥ 68，各种条数都压得住。
     */
    private int rowHeight(int lines) {
        return 15 + Math.max(1, lines) * 18;
    }

    /** 内容单元格折行数（D14）：逐条各自折行再求和 —— 按整串长度估会把多条挤成一条的高度。 */
    private int contentLines(String raw, double perLine) {
        List<ContentSeg> segs = parseContentSegs(raw);
        if (segs.isEmpty()) {
            return 1;
        }
        int lines = 0;
        for (ContentSeg g : segs) {
            int len = g.text().length() + (g.label() == null ? 0 : g.label().length() + 1)
                + (g.ord() == null ? 0 : g.ord().length() + 1);
            lines += Math.max(1, (int) Math.ceil(len / perLine));
        }
        return Math.max(1, lines);
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** 该账本的全部绑定（按学生 id 升序）。 */
    private List<BizStudentAccountLink> linksOf(Long accountId) {
        return linkMapper.selectList(new LambdaQueryWrapper<BizStudentAccountLink>()
            .eq(BizStudentAccountLink::getAccountId, accountId)
            .orderByAsc(BizStudentAccountLink::getStudentId));
    }

    private List<String> studentNamesOf(Long accountId) {
        List<String> names = new ArrayList<>();
        for (BizStudentAccountLink l : linksOf(accountId)) {
            String n = studentName(l.getStudentId());
            if (n != null && !names.contains(n)) {
                names.add(n);
            }
        }
        return names;
    }

    private String studentName(Long studentId) {
        BizStudent st = studentId == null ? null : studentMapper.selectById(studentId);
        return st == null ? null : st.getName();
    }

    /**
     * 「学生视角」账本 VO（listAccounts / roster accounts 角标同源）。
     * 🔴 M5 过渡兼容：studentId/subject/lessonPrice/amountRemain 全部照旧回吐（派生值），旧 FE 不断粮。
     */
    private Map<String, Object> accountVo(BizTuitionAccount a, BizStudentAccountLink l) {
        BigDecimal price = nz(a.getPricePerHour());
        BigDecimal hpl = hoursPerLessonOf(l);
        BigDecimal remain = nz(a.getHoursRemain());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("accountId", String.valueOf(a.getId()));
        m.put("name", a.getName());
        m.put("studentId", l.getStudentId() == null ? null : String.valueOf(l.getStudentId()));
        m.put("subject", l.getSubject());
        m.put("subjectLabel", EduTermUtil.subjectLabel(l.getSubject()));
        m.put("pricePerHour", num(price));
        m.put("hoursPerLesson", num(hpl));
        m.put("hoursRemain", num(remain));
        m.put("lessonsRemain", num(scale2(remain.divide(hpl, 6, RoundingMode.HALF_UP))));
        // 🔄 旧契约派生值（M5）：节价 = 时薪 × 每节时长；剩余金额 = 剩余小时 × 时薪
        m.put("lessonPrice", num(scale2(price.multiply(hpl))));
        m.put("amountRemain", num(scale2(remain.multiply(price))));
        m.put("shared", linksOf(a.getId()).size() > 1);
        m.put("status", a.getStatus());
        m.put("note", a.getNote());
        return m;
    }

    /** 「账本视角」VO（我的全部账本 / 台账头部）：带绑定学生列表，含零绑定账本。 */
    private Map<String, Object> accountBookVo(BizTuitionAccount a) {
        BigDecimal price = nz(a.getPricePerHour());
        BigDecimal remain = nz(a.getHoursRemain());
        List<BizStudentAccountLink> links = linksOf(a.getId());
        List<Map<String, Object>> bindings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BizStudentAccountLink l : links) {
            Map<String, Object> b = new LinkedHashMap<>();
            String n = studentName(l.getStudentId());
            b.put("studentId", l.getStudentId() == null ? null : String.valueOf(l.getStudentId()));
            b.put("studentName", n);
            b.put("subject", l.getSubject());
            b.put("subjectLabel", EduTermUtil.subjectLabel(l.getSubject()));
            b.put("hoursPerLesson", num(hoursPerLessonOf(l)));
            bindings.add(b);
            if (n != null && !names.contains(n)) {
                names.add(n);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("name", a.getName());
        m.put("pricePerHour", num(price));
        m.put("hoursRemain", num(remain));
        m.put("amountRemain", num(scale2(remain.multiply(price))));
        m.put("status", a.getStatus());
        m.put("note", a.getNote());
        m.put("bindingCount", links.size());
        m.put("shared", links.size() > 1);
        m.put("studentNames", names);
        m.put("bindings", bindings);
        // 单绑账本可折节展示；共享本基准不唯一，只报小时（规则②）
        if (links.size() == 1) {
            BigDecimal hpl = hoursPerLessonOf(links.get(0));
            m.put("hoursPerLesson", num(hpl));
            m.put("lessonsRemain", num(scale2(remain.divide(hpl, 6, RoundingMode.HALF_UP))));
            m.put("lessonPrice", num(scale2(price.multiply(hpl))));
        }
        return m;
    }

    /** 账本归属校验（不区分不存在/无权，防存在性探测；同 FeedbackSheetService 口径）。 */
    public BizTuitionAccount requireOwnedAccount(Long id) {
        if (id == null) {
            throw new ServiceException("账本 id 必填", 400);
        }
        BizTuitionAccount a = accountMapper.selectById(id);
        if (a == null) {
            throw new ServiceException("账本不存在或无权访问", 403);
        }
        requireOwned(a);
        return a;
    }

    private void requireOwned(BizTuitionAccount a) {
        if (!ownedBy(a)) {
            throw new ServiceException("账本不存在或无权访问", 403);
        }
    }

    private boolean ownedBy(BizTuitionAccount a) {
        Long uid = LoginHelper.getUserId();
        return uid != null && uid.equals(a.getCreateBy());
    }

    /** 场次起止（09:00-10:30）。 */
    private String timeRange(BizScheduleSession s) {
        String a = trimSec(s.getStartTime());
        String b = trimSec(s.getEndTime());
        if (a == null && b == null) return null;
        return (a == null ? "" : a) + "-" + (b == null ? "" : b);
    }

    private String trimSec(String t) {
        if (t != null && t.length() >= 5) return t.substring(0, 5);
        return t;
    }

    /** 课次标题（同 ScheduleTargetService 口径）。 */
    private String sessionTitle(BizScheduleSession s) {
        if ("3".equals(s.getSessionType())) return s.getExternalTitle();
        if (s.getPlanLessonId() != null) {
            BizCoursePlanLesson l = lessonMapper.selectById(s.getPlanLessonId());
            if (l != null && l.getTitle() != null) return l.getTitle();
        }
        return "2".equals(s.getSessionType()) ? "测试" : "正课";
    }

    private String localDate(Date d) {
        if (d == null) return null;
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal d) return d;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    /**
     * 🔴 JSON 数值口径：全局 JacksonConfig 把 BigDecimal 序列化成<b>字符串</b>（ToStringSerializer），
     * 而契约正本 account.ts 里 pricePerHour/hoursRemain/hoursDelta… 全是 number。
     * 故 VO 出参统一过本方法转 Double（两位小数，double 表达无损），保证 FE 拿到的是数字不是 "300.00"。
     */
    private Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private BigDecimal scale2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale4(BigDecimal v) {
        return nz(v).setScale(4, RoundingMode.HALF_UP);
    }

    /** 去尾零显示（1.00→1，0.67→0.67）。 */
    private String plain(BigDecimal v) {
        if (v == null) return "0";
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0, RoundingMode.HALF_UP).toPlainString() : s.toPlainString();
    }

    /** 台账行里的数值列已转 Double（见 {@link #num}），出图时统一去尾零显示。 */
    private String plainObj(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal d) return plain(d);
        if (v instanceof Number n) return plain(BigDecimal.valueOf(n.doubleValue()));
        return String.valueOf(v);
    }

    /** 带符号显示（+2 / -1 / -0.67；0 不加号）。 */
    private String signed(Object v) {
        String s = plainObj(v);
        if (s.isEmpty() || s.startsWith("-") || "0".equals(s)) return s;
        return "+" + s;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
