package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.TuitionAccountBo;
import org.dromara.book.domain.bo.TuitionFlowBo;
import org.dromara.book.service.schedule.TuitionAccountService;
import org.dromara.common.core.domain.R;
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

    /** 某学生的全部学科账户 → [{id,studentId,subject,lessonPrice,hoursRemain,amountRemain,status}]。 */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam Long studentId) {
        return R.ok(accountService.listAccounts(studentId));
    }

    /** 开户 / 改单价（uk(student_id,subject) 冲突 = 改单价语义）→ {id}。 */
    @SaCheckLogin
    @PostMapping
    public R<Map<String, Object>> upsert(@RequestBody TuitionAccountBo bo) {
        Long id = accountService.upsertAccount(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /** 手工流水（'1' 充值 / '4' 调整）→ {id}（流水 id）。事务内插流水 + 更新余额。 */
    @SaCheckLogin
    @PostMapping("/{id}/flow")
    public R<Map<String, Object>> flow(@PathVariable Long id, @RequestBody TuitionFlowBo bo) {
        Long flowId = accountService.addFlow(id, bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(flowId));
        return R.ok(r);
    }

    /** 消耗台账（流水 join 场次，倒序分页）→ {rows,total}。 */
    @SaCheckLogin
    @GetMapping("/{id}/ledger")
    public R<Map<String, Object>> ledger(@PathVariable Long id,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) Integer pageNum,
                                         @RequestParam(required = false) Integer pageSize) {
        return R.ok(accountService.ledger(id, startDate, endDate, pageNum, pageSize));
    }

    /** 课时流水单导出 PNG（D16）→ {file,url}（下载复用 /teacher/schedule/artifact）。 */
    @SaCheckLogin
    @PostMapping("/{id}/export-ledger-png")
    public R<Map<String, Object>> exportLedgerPng(@PathVariable Long id) {
        return R.ok(accountService.exportLedgerPng(id));
    }
}
