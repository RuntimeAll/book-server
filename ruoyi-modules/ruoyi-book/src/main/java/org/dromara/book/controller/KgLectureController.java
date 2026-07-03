package org.dromara.book.controller;

import lombok.RequiredArgsConstructor;
import org.dromara.book.service.IKgLectureService;
import org.dromara.common.core.domain.R;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 讲义浏览/编辑接口（PRD-C-207）—— 讲义 = 挂 KG 节点的原子片段，按前缀树序汇聚。
 *
 * <p>前缀 {@code /teacher/kg/**}，命中 MisiktEnvelopeAdvice。读端点在免登白名单（匿名=只见官方），
 * FE 带 token 时按可见性（官方+本机构管理员+我的）返回；🔴 写端点 {@code /lecture-frag} 不在白名单，
 * Sa-Token 强制登录，owner 强制=登录者（D12 极简 owner 制唯一写规则）。
 * example 节点仍走既有 {@code GET /teacher/kg/questions?qids=}。
 *
 * @author codeplace-C PRD-C-207
 */
@RestController
@RequestMapping("/teacher/kg")
@RequiredArgsConstructor
public class KgLectureController {

    private final IKgLectureService kgLectureService;

    /** 读端点在白名单：登录态可有可无，拿得到就用于可见性。 */
    private static Long viewerOrNull() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception ignore) {
            return null;
        }
    }

    @GetMapping("/lecture")
    public R<Map<String, Object>> lecture(@RequestParam("subjectId") String subjectId,
                                          @RequestParam(value = "bookId", required = false) String bookId,
                                          @RequestParam(value = "owner", required = false) Long owner) {
        return R.ok(kgLectureService.getLecture(subjectId, bookId, owner, viewerOrNull()));
    }

    @GetMapping("/lecture-catalog")
    public R<Map<String, Object>> catalog(@RequestParam(value = "bookId", required = false) String bookId) {
        return R.ok(kgLectureService.getCatalog(bookId, viewerOrNull()));
    }

    /**
     * 批量保存片段（编辑态保存 / 讲义录入共用，PRD-C-207 V7 = PRD-C-210 save_lecture_frag 同口）。
     * body: {bookId, owner?, frags:[{subjectId, title, contentJson, status?}]}
     * owner 缺省=存自己名下；传了按写校验序判权（管理员改本部门成员的，权限模型 §4）。
     */
    @PostMapping("/lecture-frag")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> saveFrags(@RequestBody Map<String, Object> body) {
        long loginId = LoginHelper.getUserId(); // 不在白名单，Sa-Token 已保证登录
        String bookId = str(body.get("bookId"));
        Long owner = body.get("owner") == null ? null : Long.valueOf(body.get("owner").toString());
        Object frags = body.get("frags");
        if (!(frags instanceof List) || ((List<?>) frags).isEmpty()) {
            return R.fail("frags 不能为空");
        }
        return R.ok(kgLectureService.saveFrags(bookId, owner, (List<Map<String, Object>>) frags, loginId));
    }

    /** 管理页：下架/删除某 owner 某前缀的片段（同写校验序；prefix 至少到节防误删）。 */
    @PostMapping("/lecture-frag/remove")
    public R<Map<String, Object>> removeFrags(@RequestBody Map<String, Object> body) {
        long loginId = LoginHelper.getUserId();
        if (body.get("owner") == null) {
            return R.fail("owner 必填");
        }
        return R.ok(kgLectureService.removeFrags(str(body.get("bookId")),
            Long.parseLong(body.get("owner").toString()), str(body.get("subjectPrefix")), loginId));
    }

    /** 管理页：成员列表（superadmin=全部 / org_admin=本部门；其余 403）。 */
    @GetMapping("/manage/members")
    public R<List<Map<String, Object>>> members() {
        return R.ok(kgLectureService.getMembers(LoginHelper.getUserId()));
    }

    /** 管理页：讲义清单（owner×课时聚合，范围同上）。 */
    @GetMapping("/manage/lectures")
    public R<List<Map<String, Object>>> manageLectures() {
        return R.ok(kgLectureService.getManageLectures(LoginHelper.getUserId()));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
