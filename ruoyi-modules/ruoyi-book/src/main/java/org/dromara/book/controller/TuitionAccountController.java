package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.TuitionAccountBo;
import org.dromara.book.domain.bo.TuitionFlowBo;
import org.dromara.book.domain.bo.TuitionTransferBo;
import org.dromara.book.service.schedule.TuitionAccountService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课时课费账户 Controller（PRD-015 /teacher/schedule/account）。命中 MisiktEnvelopeAdvice（envelope）。
 *
 * <p>契约正本 = book-ui {@code src/api/teacher/account.ts}（批 0 定稿）：
 * list / upsert / flow / ledger / export-ledger-png 五端点，全部 @SaCheckLogin + create_by 归属。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/schedule/account")
@RequiredArgsConstructor
public class TuitionAccountController {

    private final TuitionAccountService accountService;

    /**
     * 某学生的全部学科账本（走绑定表反查）
     * → [{id,accountId,studentId,subject,pricePerHour,hoursPerLesson,hoursRemain,lessonsRemain,
     * lessonPrice(派生),amountRemain(派生),shared,status}]。
     */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam Long studentId) {
        return R.ok(accountService.listAccounts(studentId));
    }

    /**
     * 我的全部账本（PRD-018 M6，additive）：按 create_by 列本人所有账本，<b>含零绑定账本</b>——
     * 改绑后旧本的冲正退款永远查得到，不会变成任何页面都看不到又删不掉的孤儿。
     */
    @SaCheckLogin
    @GetMapping("/all")
    public R<List<Map<String, Object>>> all() {
        return R.ok(accountService.listMyAccounts());
    }

    /**
     * 开户 / 改绑 / 改本（v3：建账本 + 建绑定同一事务，普通学生无感知）→ {id}（账本 id）。
     * uk(student_id,subject) 命中 = 改该绑定的每节时长 + 该账本时薪（不报错、不动余额）。
     */
    @SaCheckLogin
    @PostMapping
    public R<Map<String, Object>> upsert(@RequestBody TuitionAccountBo bo) {
        Long id = accountService.upsertAccount(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /**
     * 账本转移（PRD-018 M6-2，additive）：一个事务产一对 '4' 调整行并互写 rel_flow_id，
     * 让换本/拆本在台账上是一笔可解释的动作 → {fromFlowId,toFlowId,hours}。
     */
    @SaCheckLogin
    @PostMapping("/transfer")
    public R<Map<String, Object>> transfer(@RequestBody TuitionTransferBo bo) {
        return R.ok(accountService.transfer(bo));
    }

    /**
     * 停用 / 启用账户（bug 批 BUG-3/A，additive）：body {"status":"0"|"1"}。
     * 停用后该学科不进建计划下拉、结算取不到账户；余额与流水原样保留。
     */
    @SaCheckLogin
    @PostMapping("/{id}/status")
    public R<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        accountService.setStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }

    /**
     * 删户（bug 批 BUG-3/A，additive）：<b>仅零流水账户可硬删</b>，
     * 有流水返 400「请改为停用」（审计线不可断）。
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return R.ok();
    }

    /**
     * 手工流水（'1' 充值 / '4' 调整）→ {id}（流水 id）。事务内插流水 + 更新余额缓存。
     * 三档输入任给其一（hours / lessons / amount，D9）+ occurDate（默认今天，补录可回填历史）。
     */
    @SaCheckLogin
    @PostMapping("/{id}/flow")
    public R<Map<String, Object>> flow(@PathVariable Long id, @RequestBody TuitionFlowBo bo) {
        Long flowId = accountService.addFlow(id, bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(flowId));
        return R.ok(r);
    }

    /**
     * 消耗台账（PRD-018 D2 实时推导）→ {rows,total,pageNum,pageSize,pages,openingBalance,openingAmount,
     * mode,cycleStart,account,shared}。
     * 🔴 全量流水按业务日期<b>正序</b>累加出逐行剩余，再切片；pageNum 不传 = 最后一页（最近的行）。
     * 🔴 {@code mode=cycle}（D13）= 只看最近一个重置周期（最后一笔充值起至今）；
     * <b>不传 = 全量</b>（页面浏览口径不变），给了 startDate/endDate 则 mode 失效走区间。
     */
    @SaCheckLogin
    @GetMapping("/{id}/ledger")
    public R<Map<String, Object>> ledger(@PathVariable Long id,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) String mode,
                                         @RequestParam(required = false) Integer pageNum,
                                         @RequestParam(required = false) Integer pageSize) {
        return R.ok(accountService.ledger(id, startDate, endDate, mode, pageNum, pageSize));
    }

    /**
     * 课时流水单导出 PNG（D16）→ {file,url,mode,cycleStart,rows}（下载复用 /teacher/schedule/artifact）。
     *
     * <p>🔴 <b>D13 导出口径</b>：body 可空 —— <b>不传 = 最近重置周期</b>（最后一笔充值起至今，
     * 家长要看的就是「这期钱用到哪儿了」）。要别的范围就传 {@code {"startDate":"","endDate":""}}；
     * 要整本账传 {@code {"mode":"all"}}。
     */
    @SaCheckLogin
    @PostMapping("/{id}/export-ledger-png")
    public R<Map<String, Object>> exportLedgerPng(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> b = body == null ? Map.of() : body;
        return R.ok(accountService.exportLedgerPng(id, b.get("startDate"), b.get("endDate"), b.get("mode")));
    }
}
