package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.TuitionAccountBo;
import org.dromara.book.domain.bo.TuitionFlowBo;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.domain.entity.BizTuitionAccount;
import org.dromara.book.domain.entity.BizTuitionFlow;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
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
 * 课时课费账户 Service（PRD-015 D2/D3/D16，/teacher/schedule/account/**）。
 *
 * <p><b>模型</b>：一行账户 = 一个「学生 × 学科」绑定（开户即绑定学科，uk_student_subject）；
 * 余额可为负（欠费不拦截，老师最高权限）。全部余额变动<b>只经流水行</b>产生（审计线，D1 铁律），
 * 每笔定格 hours_after / amount_after 快照 = 台账「剩余」列，历史行不随后续变动漂移。
 *
 * <p><b>归属</b>：create_by = 登录老师，查/改/流水/导出一律 owner 过滤，防水平越权（同 FeedbackSheetService）。
 *
 * <p><b>台账</b>（ledger）= 流水 join 场次的统一时间线：扣课/冲正行取场次日期+起止+课次标题，
 * 充值/调整行取流水时间+备注，倒序分页——直接渲染手抄课时本五列语义。
 *
 * <p><b>流水单 PNG</b>（D16）= Excel 风格格式化表格（标题栏 + meta 行 + 网格表升序，
 * 充值行绿 / 冲正行红，🔴 无合计行），复用 {@link ScheduleRenderUtil#renderToPng}
 * （HTML→openhtmltopdf→pdfbox 光栅化，纯 Java 进程内，同反馈单导出链）。
 * 🔴 家长可见物：零内部词。
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

    private final BizTuitionAccountMapper accountMapper;
    private final BizTuitionFlowMapper flowMapper;
    private final BizStudentMapper studentMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final ScheduleRenderUtil renderUtil;

    // ─────────────────────────── 账户 CRUD ───────────────────────────

    /** 某学生的全部学科账户（owner 过滤，按学科码升序）。 */
    public List<Map<String, Object>> listAccounts(Long studentId) {
        if (studentId == null) {
            throw new ServiceException("请指定学生（studentId 必填）", 400);
        }
        List<BizTuitionAccount> list = accountMapper.selectList(new LambdaQueryWrapper<BizTuitionAccount>()
            .eq(BizTuitionAccount::getCreateBy, LoginHelper.getUserId())
            .eq(BizTuitionAccount::getStudentId, studentId)
            .orderByAsc(BizTuitionAccount::getSubject));
        List<Map<String, Object>> out = new ArrayList<>();
        for (BizTuitionAccount a : list) {
            out.add(accountVo(a));
        }
        return out;
    }

    /**
     * 开户 / 改单价。uk(student_id, subject) 冲突 = <b>改单价语义</b>（不报错、不动余额）。
     *
     * @return 账户 id
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
        BigDecimal price = scale2(bo.getLessonPrice());
        if (price.signum() < 0) {
            throw new ServiceException("课时单价不能为负", 400);
        }
        // uk 是全局唯一（不含 create_by），先按 uk 查再判归属，避免撞唯一索引抛裸 500
        BizTuitionAccount exist = accountMapper.selectOne(new LambdaQueryWrapper<BizTuitionAccount>()
            .eq(BizTuitionAccount::getStudentId, bo.getStudentId())
            .eq(BizTuitionAccount::getSubject, subject)
            .last("LIMIT 1"));
        if (exist != null) {
            requireOwned(exist);
            exist.setLessonPrice(price);
            if (bo.getNote() != null) {
                exist.setNote(bo.getNote());
            }
            accountMapper.updateById(exist);
            return exist.getId();
        }
        BizTuitionAccount e = new BizTuitionAccount();
        e.setStudentId(bo.getStudentId());
        e.setSubject(subject);
        e.setLessonPrice(price);
        e.setHoursRemain(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        e.setAmountRemain(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        e.setStatus("0");
        e.setNote(bo.getNote());
        e.setCreateBy(LoginHelper.getUserId());
        accountMapper.insert(e);
        return e.getId();
    }

    /** 学生是否已开通该学科账户（计划学科归属校验用，PRD-015 §9 班级跳过）。 */
    public boolean hasAccount(Long studentId, String subject) {
        if (studentId == null || subject == null || subject.isBlank()) {
            return false;
        }
        return accountMapper.selectCount(new LambdaQueryWrapper<BizTuitionAccount>()
            .eq(BizTuitionAccount::getCreateBy, LoginHelper.getUserId())
            .eq(BizTuitionAccount::getStudentId, studentId)
            .eq(BizTuitionAccount::getSubject, subject)) > 0;
    }

    // ─────────────────────────── 手工流水（充值 / 调整） ───────────────────────────

    /**
     * 手工流水：充值('1') / 调整('4')。事务内「插流水（含 after 快照） + 更新账户余额」。
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
        BigDecimal hours = scale2(bo.getHoursDelta());
        BigDecimal amount = scale2(bo.getAmountDelta());
        if (hours.signum() == 0 && amount.signum() == 0) {
            throw new ServiceException("课时与金额不能同时为 0", 400);
        }
        BizTuitionFlow f = applyFlow(acc, type, hours, amount, null, bo.getNote());
        return f.getId();
    }

    /**
     * 记一笔流水并同步账户余额（<b>唯一的余额写入口</b>，禁止别处裸 UPDATE 余额列）。
     * 扣课/冲正走结算链时也调本方法（批 3），故此处不限制类型。
     *
     * @param acc       账户（调用方已做归属校验）
     * @param flowType  '1' 充值 / '2' 扣课 / '3' 冲正 / '4' 调整
     * @param hours     课时增量（扣为负）
     * @param amount    金额增量（扣为负）
     * @param sessionId 关联场次（扣课/冲正必填=幂等键；手工流水传 null）
     * @param note      备注
     * @return 落库后的流水行（含 after 快照）
     */
    @Transactional(rollbackFor = Exception.class)
    public BizTuitionFlow applyFlow(BizTuitionAccount acc, String flowType, BigDecimal hours,
                                    BigDecimal amount, Long sessionId, String note) {
        BigDecimal h = scale2(hours);
        BigDecimal a = scale2(amount);
        BigDecimal hoursAfter = scale2(nz(acc.getHoursRemain()).add(h));
        BigDecimal amountAfter = scale2(nz(acc.getAmountRemain()).add(a));

        BizTuitionFlow f = new BizTuitionFlow();
        f.setAccountId(acc.getId());
        f.setFlowType(flowType);
        f.setHoursDelta(h);
        f.setAmountDelta(a);
        f.setHoursAfter(hoursAfter);
        f.setAmountAfter(amountAfter);
        f.setSessionId(sessionId);
        f.setNote(note);
        f.setCreateBy(LoginHelper.getUserId());
        flowMapper.insert(f);

        acc.setHoursRemain(hoursAfter);
        acc.setAmountRemain(amountAfter);
        accountMapper.updateById(acc);
        return f;
    }

    // ─────────────────────────── 台账（消耗记录） ───────────────────────────

    /**
     * 消耗台账（AC4）：流水 join 场次的统一时间线，倒序分页。
     * 行 = {date, timeRange, content, flowType, hoursDelta, hoursAfter, amountAfter}——
     * 扣课/冲正取场次日期+起止+课次标题；充值/调整取流水时间+备注（timeRange=null）。
     */
    public Map<String, Object> ledger(Long accountId, String startDate, String endDate,
                                      Integer pageNum, Integer pageSize) {
        requireOwnedAccount(accountId);
        List<BizTuitionFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getAccountId, accountId)
            .orderByDesc(BizTuitionFlow::getCreateTime)
            .orderByDesc(BizTuitionFlow::getId));
        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);
        List<Map<String, Object>> all = new ArrayList<>();
        for (BizTuitionFlow f : flows) {
            Map<String, Object> row = ledgerRow(f);
            LocalDate d = parseDate((String) row.get("date"));
            if (from != null && d != null && d.isBefore(from)) continue;
            if (to != null && d != null && d.isAfter(to)) continue;
            all.add(row);
        }
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 100 : pageSize;
        int fromIdx = Math.min((pn - 1) * ps, all.size());
        int toIdx = Math.min(fromIdx + ps, all.size());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", new ArrayList<>(all.subList(fromIdx, toIdx)));
        r.put("total", all.size());
        return r;
    }

    /** 台账一行（对齐 FE LedgerRowVO）。 */
    private Map<String, Object> ledgerRow(BizTuitionFlow f) {
        Map<String, Object> m = new LinkedHashMap<>();
        String date = null;
        String timeRange = null;
        String content = f.getNote();
        if (f.getSessionId() != null) {
            BizScheduleSession s = sessionMapper.selectById(f.getSessionId());
            if (s != null) {
                date = s.getSessionDate() == null ? null : s.getSessionDate().toString();
                timeRange = timeRange(s);
                String title = sessionTitle(s);
                // 冲正行内容带上原因备注（如「请假」），扣课行优先课次标题
                if (FLOW_REVERSE.equals(f.getFlowType())) {
                    content = (f.getNote() == null || f.getNote().isBlank()) ? title : title + "（" + f.getNote() + "）";
                } else {
                    content = (title == null || title.isBlank()) ? f.getNote() : title;
                }
            }
        }
        if (date == null) {
            date = localDate(f.getCreateTime());
        }
        m.put("id", f.getId() == null ? null : String.valueOf(f.getId()));
        m.put("date", date);
        m.put("timeRange", timeRange);
        m.put("content", content);
        m.put("flowType", f.getFlowType());
        m.put("hoursDelta", f.getHoursDelta());
        m.put("amountDelta", f.getAmountDelta());
        m.put("hoursAfter", f.getHoursAfter());
        m.put("amountAfter", f.getAmountAfter());
        return m;
    }

    // ─────────────────────────── 流水单导出 PNG（D16） ───────────────────────────

    /**
     * 课时流水单 PNG（D16 / AC14）：标题栏 +（单价 / 截至 / 当前剩余）meta 行 + 网格表<b>升序</b>
     * （日期 / 上课时间 / 内容 / 课时变动 / 剩余课时），充值行绿、冲正行红，🔴 无合计行。
     * 🔴 家长可见物：零内部词。
     */
    public Map<String, Object> exportLedgerPng(Long accountId) {
        BizTuitionAccount acc = requireOwnedAccount(accountId);
        BizStudent stu = studentMapper.selectById(acc.getStudentId());
        String stuName = stu == null ? "学生" : stu.getName();
        String subjectLabel = EduTermUtil.subjectLabel(acc.getSubject());

        // 升序（对账阅读序），复用台账行构造
        List<BizTuitionFlow> flows = flowMapper.selectList(new LambdaQueryWrapper<BizTuitionFlow>()
            .eq(BizTuitionFlow::getAccountId, accountId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizTuitionFlow f : flows) {
            rows.add(ledgerRow(f));
        }
        rows.sort(Comparator.comparing(x -> String.valueOf(x.get("date") == null ? "" : x.get("date"))));

        String html = buildLedgerHtml(stuName, subjectLabel, acc, rows);
        int height = 150 + Math.max(rows.size(), 1) * 30 + 26;
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

        sb.append("<div class=\"bar\">课时流水单 · ").append(esc(stuName))
            .append("（").append(esc(subjectLabel == null ? "课程" : subjectLabel)).append("）</div>");
        sb.append("<table class=\"meta\"><tr>");
        sb.append("<td>单价 <b>").append(plain(acc.getLessonPrice())).append("</b> 元/课时</td>");
        sb.append("<td>截至 <b>").append(LocalDate.now()).append("</b></td>");
        sb.append("<td>当前剩余 <b>").append(plain(acc.getHoursRemain())).append("</b> 课时 · <b>")
            .append(plain(acc.getAmountRemain())).append("</b> 元</td>");
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
            sb.append("<td>").append(esc(str(row.get("content")))).append("</td>");
            sb.append("<td class=\"r\">").append(esc(signed(row.get("hoursDelta")))).append("</td>");
            sb.append("<td class=\"r\">").append(esc(str(row.get("hoursAfter")))).append("</td>");
            sb.append("</tr>");
        }
        if (rows.isEmpty()) {
            sb.append("<tr><td class=\"c\" colspan=\"5\" style=\"color:#999;padding:14px\">（暂无记录）</td></tr>");
        }
        // 🔴 D16 明确：无合计行
        sb.append("</table></div></body></html>");
        return sb.toString();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** 账户 VO（对齐 FE TuitionAccountVO）。 */
    private Map<String, Object> accountVo(BizTuitionAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(a.getId()));
        m.put("studentId", a.getStudentId() == null ? null : String.valueOf(a.getStudentId()));
        m.put("subject", a.getSubject());
        m.put("subjectLabel", EduTermUtil.subjectLabel(a.getSubject()));
        m.put("lessonPrice", a.getLessonPrice());
        m.put("hoursRemain", a.getHoursRemain());
        m.put("amountRemain", a.getAmountRemain());
        m.put("status", a.getStatus());
        m.put("note", a.getNote());
        return m;
    }

    /** 账户归属校验（不区分不存在/无权，防存在性探测；同 FeedbackSheetService 口径）。 */
    public BizTuitionAccount requireOwnedAccount(Long id) {
        if (id == null) {
            throw new ServiceException("账户 id 必填", 400);
        }
        BizTuitionAccount a = accountMapper.selectById(id);
        if (a == null) {
            throw new ServiceException("账户不存在或无权访问", 403);
        }
        requireOwned(a);
        return a;
    }

    private void requireOwned(BizTuitionAccount a) {
        if (!LoginHelper.getUserId().equals(a.getCreateBy())) {
            throw new ServiceException("账户不存在或无权访问", 403);
        }
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

    private BigDecimal scale2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** 去尾零显示（1.00→1，0.67→0.67）。 */
    private String plain(BigDecimal v) {
        if (v == null) return "0";
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0, RoundingMode.HALF_UP).toPlainString() : s.toPlainString();
    }

    /** 带符号显示（+2 / -1 / -0.67）。 */
    private String signed(Object v) {
        if (!(v instanceof BigDecimal d)) return str(v);
        String s = plain(d);
        return d.signum() > 0 ? "+" + s : s;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
