package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.service.punch.PunchService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日打卡书 Controller（PRD-013 /teacher/punch）。
 *
 * <p>🔴 必须落 {@code org.dromara.book.controller} 包 —— MisiktEnvelopeAdvice 按 basePackages 装配，
 * /teacher/ 前缀响应转 misikt envelope {code:1,message,response}。
 *
 * <p>🔴 收 JSON 含题面/讲义内容（数学 {@code < >} 不等式）→ application.yml xss.excludeUrls
 * 已加 {@code /teacher/punch/**}，否则 XssFilter 静默剥 {@code <...>} 腐蚀内容/损坏 JSON。
 *
 * <p>路由：
 * <pre>
 *   GET  /teacher/punch/days?bookId=                      目录 {days:[{day,nodeId,itemCount,review}]}
 *   GET  /teacher/punch/day?bookId=&amp;day=                  一天原文 {goals,modules,review}
 *   GET  /teacher/punch/preview?bookId=&amp;day=&amp;paper=       纸面 HTML 串（FE iframe，与导出同源）
 *   POST /teacher/punch/exportDay  {bookId,day,papers?}   双卷 PDF → OSS {questionUrl,answerUrl}
 *   POST /teacher/punch/upsertDay  {bookId,day,goals,modules}  幂等建/覆盖 → {nodeId,itemIds}
 *   POST /teacher/punch/review     {bookId,day,action,issues?} 审核流 → {review}
 * </pre>
 *
 * <p>🔴 bookId/day/qid 在 JSON 里可能是字符串（雪花号 JSON double 截尾防护），一律 String.valueOf 再转。
 *
 * @author backend-dev (PRD-013 批1-BE)
 */
@RestController
@RequestMapping("/teacher/punch")
@RequiredArgsConstructor
public class PunchController {

    private final PunchService punchService;

    @SaCheckLogin
    @GetMapping("/days")
    public R<Map<String, Object>> days(@RequestParam String bookId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", bookId);
        r.put("days", punchService.listDays(reqId(bookId, "bookId")));
        return R.ok(r);
    }

    @SaCheckLogin
    @GetMapping("/day")
    public R<Map<String, Object>> day(@RequestParam String bookId, @RequestParam String day) {
        return R.ok(punchService.getDay(reqId(bookId, "bookId"), reqInt(day, "day")));
    }

    /**
     * 纸面预览 HTML（FE iframe srcdoc/blob 用；与 exportDay 同 theme+data）。
     *
     * <p>🔴 返回 {@code {html:"..."}} 包一层 —— {@code R.ok(String)} 会命中 msg 重载
     * （HTML 掉进 envelope 的 message、response 为空），Java 重载坑，勿改回裸 String。
     */
    @SaCheckLogin
    @GetMapping("/preview")
    public R<Map<String, Object>> preview(@RequestParam String bookId,
                                          @RequestParam String day,
                                          @RequestParam(required = false) String paper) {
        String html = punchService.previewHtml(reqId(bookId, "bookId"), reqInt(day, "day"), paper);
        return R.ok(Map.of("html", html));
    }

    @SaCheckLogin
    @PostMapping("/exportDay")
    public R<Map<String, Object>> exportDay(@RequestBody Map<String, Object> body) {
        return R.ok(punchService.exportDay(
            reqId(body.get("bookId"), "bookId"),
            reqInt(body.get("day"), "day"),
            strList(body.get("papers"))));
    }

    /**
     * 整书导出（2026-07-30）：全部天合并成册，每种卷一份全册 PDF。
     * 同步长任务（10 天双卷约 40-80s），FE 调用侧超时放宽到 5 分钟。
     */
    @SaCheckLogin
    @PostMapping("/exportBook")
    public R<Map<String, Object>> exportBook(@RequestBody Map<String, Object> body) {
        return R.ok(punchService.exportBook(
            reqId(body.get("bookId"), "bookId"),
            strList(body.get("papers"))));
    }

    @SaCheckLogin
    @PostMapping("/upsertDay")
    public R<Map<String, Object>> upsertDay(@RequestBody Map<String, Object> body) {
        return R.ok(punchService.upsertDay(
            reqId(body.get("bookId"), "bookId"),
            reqInt(body.get("day"), "day"),
            strList(body.get("goals")),
            mapList(body.get("modules"))));
    }

    @SaCheckLogin
    @PostMapping("/review")
    public R<Map<String, Object>> review(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> issues = body.containsKey("issues") ? mapList(body.get("issues")) : null;
        return R.ok(punchService.submitReview(
            reqId(body.get("bookId"), "bookId"),
            reqInt(body.get("day"), "day"),
            body.get("action") == null ? "" : String.valueOf(body.get("action")),
            issues));
    }

    // ───────────────── 入参解析（雪花号字符串防护） ─────────────────

    private Long reqId(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ServiceException(field + " 不能为空", 400);
        }
        String s = String.valueOf(raw).trim();
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new ServiceException(field + " 非法：" + s, 400);
        }
    }

    private int reqInt(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ServiceException(field + " 不能为空", 400);
        }
        String s = String.valueOf(raw).trim();
        try {
            return (int) Double.parseDouble(s);   // JSON 数字可能带 .0
        } catch (NumberFormatException e) {
            throw new ServiceException(field + " 非法：" + s, 400);
        }
    }

    private List<String> strList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) out.add(String.valueOf(o));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map) out.add((Map<String, Object>) o);
            }
        }
        return out;
    }
}
