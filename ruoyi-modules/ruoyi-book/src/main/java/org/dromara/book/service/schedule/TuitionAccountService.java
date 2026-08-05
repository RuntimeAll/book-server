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

    /**
     * 学生是否已绑定该学科账本（计划学科归属校验用，PRD-015 §9 班级跳过）。
     * 🔴 bug 批 BUG-3/A：<b>停用账本不算开通</b>。签名不变，底层从账户表换成绑定表，调用方零改动。
     */
    public boolean hasAccount(Long studentId, String subject) {
        BizStudentAccountLink l = findLink(studentId, subject);
        if (l == null) {
            return false;
        }
        BizTuitionAccount a = accountMapper.selectById(l.getAccountId());
        return a != null && !STATUS_DISABLED.equals(a.getStatus());
    }

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
                throw new ServiceException("该账本还没有设置时薪，无法按金额换算课时", 400);
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
     * @return {rows, total, pageNum, pageSize, pages, openingBalance, openingAmount, shared, hoursRemain, ...}
     */
    public Map<String, Object> ledger(Long accountId, String startDate, String endDate,
                                      Integer pageNum, Integer pageSize) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        List<Map<String, Object>> all = buildLedgerRows(acc);

        // 区间切片（剩余已在全量累加阶段算完，区间不影响任何一行的剩余值，M10）
        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : all) {
            LocalDate d = parseDate((String) row.get("date"));
            if (from != null && d != null && d.isBefore(from)) continue;
            if (to != null && d != null && d.isAfter(to)) continue;
            filtered.add(row);
        }

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
        r.put("account", accountBookVo(acc));
        r.put("shared", linksOf(acc.getId()).size() > 1);
        return r;
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
            int group = s == null ? 0 : 1;
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

    /** 台账「内容」列：场次行取课次标题（冲正带原因备注），手工行取备注。 */
    private String contentOf(BizTuitionFlow f, BizScheduleSession s) {
        if (s == null) {
            return f.getNote();
        }
        String title = sessionTitle(s);
        if (FLOW_REVERSE.equals(f.getFlowType())) {
            return (f.getNote() == null || f.getNote().isBlank()) ? title : title + "（" + f.getNote() + "）";
        }
        return (title == null || title.isBlank()) ? f.getNote() : title;
    }

    // ─────────────────────────── 流水单导出 PNG（D16） ───────────────────────────

    /**
     * 课时流水单 PNG（D16 / AC14）：标题栏 +（时薪 / 截至 / 当前剩余）meta 行 + 网格表<b>升序</b>
     * （日期 / 上课时间 / 内容 / 课时变动 / 剩余课时），充值行绿、冲正行红，🔴 无合计行。
     * 🔴 家长可见物：零内部词。行序与 {@link #ledger} 同源（buildLedgerRows），保证逐行一致。
     */
    public Map<String, Object> exportLedgerPng(Long accountId) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        List<BizStudentAccountLink> links = linksOf(accountId);
        String title = String.join("+", studentNamesOf(accountId));
        if (title.isBlank()) {
            title = acc.getName() == null || acc.getName().isBlank() ? "课时账本" : acc.getName();
        }
        String subjectLabel = links.size() == 1 ? EduTermUtil.subjectLabel(links.get(0).getSubject()) : null;

        List<Map<String, Object>> rows = buildLedgerRows(acc);
        String html = buildLedgerHtml(title, subjectLabel, acc, rows);
        // 高度收边（同 FeedbackSheetService BUG-014 口径）：标题条+meta 行+表头+内边距 ≈ 124px，
        // 每行 27px，内容列约 30 个中文字换行 → 长内容多算一行不裁切、短行不留白底。
        int rowsH = 0;
        for (Map<String, Object> row : rows) {
            int lines = Math.max(1, (int) Math.ceil(str(row.get("content")).length() / 30.0));
            rowsH += lines * 27;
        }
        if (rows.isEmpty()) {
            rowsH = 34;
        }
        int height = 124 + rowsH + 14;
        String file = renderUtil.renderToPng(html, "ledger_" + accountId, 720, height);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("url", "/teacher/schedule/artifact?path=" + file);
        return m;
    }

    /** Excel 风格流水单 HTML（openhtmltopdf 不支持 flex/grid → 纯 table 布局）。 */
    private String buildLedgerHtml(String stuName, String subjectLabel, BizTuitionAccount acc,
                                   List<Map<String, Object>> rows) {
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
        sb.append("tr.in td{background:#f2fbf5;color:#15803d}");
        sb.append("tr.rev td{background:#fdf2f2;color:#b91c1c}");
        sb.append("</style></head><body><div class=\"wrap\">");

        sb.append("<div class=\"bar\">课时流水单 · ").append(esc(stuName));
        if (subjectLabel != null && !subjectLabel.isBlank()) {
            sb.append("（").append(esc(subjectLabel)).append("）");
        }
        sb.append("</div>");
        BigDecimal remain = nz(acc.getHoursRemain());
        sb.append("<table class=\"meta\"><tr>");
        sb.append("<td>单价 <b>").append(plain(nz(acc.getPricePerHour()))).append("</b> 元/小时</td>");
        sb.append("<td>截至 <b>").append(LocalDate.now()).append("</b></td>");
        sb.append("<td>当前剩余 <b>").append(plain(remain)).append("</b> 小时 · <b>")
            .append(plain(scale2(remain.multiply(nz(acc.getPricePerHour()))))).append("</b> 元</td>");
        sb.append("</tr></table>");

        sb.append("<table class=\"grid\">");
        sb.append("<tr>")
            .append("<th style=\"width:92px\">日期</th>")
            .append("<th style=\"width:104px\">上课时间</th>")
            .append("<th>内容</th>")
            .append("<th style=\"width:88px\">课时变动</th>")
            .append("<th style=\"width:88px\">剩余课时</th>")
            .append("</tr>");
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("flowType"));
            String cls = FLOW_RECHARGE.equals(type) ? " class=\"in\"" : (FLOW_REVERSE.equals(type) ? " class=\"rev\"" : "");
            sb.append("<tr").append(cls).append(">");
            sb.append("<td class=\"c\">").append(esc(str(row.get("date")))).append("</td>");
            sb.append("<td class=\"c\">").append(esc(str(row.get("timeRange")))).append("</td>");
            sb.append("<td>").append(esc(contentCell(row))).append("</td>");
            sb.append("<td class=\"r\">").append(esc(signed(row.get("hoursDelta")))).append("</td>");
            sb.append("<td class=\"r\">").append(esc(plainObj(row.get("hoursAfter")))).append("</td>");
            sb.append("</tr>");
        }
        if (rows.isEmpty()) {
            sb.append("<tr><td class=\"c\" colspan=\"5\" style=\"color:#999;padding:14px\">（暂无记录）</td></tr>");
        }
        // 🔴 D16 明确：无合计行
        sb.append("</table></div></body></html>");
        return sb.toString();
    }

    /** 共享账本的场次行在内容前挂学生名（规则①）。 */
    private String contentCell(Map<String, Object> row) {
        String name = str(row.get("studentName"));
        String content = str(row.get("content"));
        return name.isBlank() ? content : name + " · " + content;
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
