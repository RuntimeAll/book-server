package org.dromara.bookadmin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SubjectLazyTreeBo;
import org.dromara.book.domain.vo.MisiktPageVo;
import org.dromara.book.domain.vo.QuestionDetailVo;
import org.dromara.book.domain.vo.QuestionItemVo;
import org.dromara.book.domain.vo.SubjectNodeVo;
import org.dromara.bookadmin.domain.bo.AdminQuestionEditBo;
import org.dromara.bookadmin.domain.bo.AdminQuestionPageBo;
import org.dromara.bookadmin.service.IAdminQuestionService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * book-admin 后台题目管理 Controller（H1 卡 段② BE 波 2 — 独立模块化重构）。
 *
 * <p>模块隔离铁则（用户 2026-05-22 拍板）：本 Controller 物理落 {@code ruoyi-book-admin}
 * 模块，禁注入 {@code org.dromara.book.service.IQuestionService}（教师端 Service）；
 * 必须注入 admin 自有 {@link IAdminQuestionService}，方法内部走 Mapper 自查。
 * admin 与 teacher 改方法互不波及。
 *
 * <p>路径前缀 {@code /admin/question/...}，命中 RuoYi 标准 {@code R<T>} envelope，
 * <strong>不</strong>走 {@code MisiktEnvelopeAdvice}（只 scope {@code /teacher/} 前缀）；
 * book-admin (plus-ui) axios 拦截器天然走 RuoYi envelope，免转换。
 *
 * <p>鉴权：每个方法独立挂 {@code @SaCheckPermission("admin:question:xxx")} 注解，
 * 权限标识在 sys_menu 表维护（V6 SQL 已挂 admin:question:list）。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/admin/question")
@RequiredArgsConstructor
public class AdminQuestionController {

    private final IAdminQuestionService adminQuestionService;

    /**
     * POST /admin/question/page — 题目分页（admin 通道，独立 service 自查 Mapper）。
     *
     * <p>本波（V-5 重构）：路由 + Sa-Token 权限注解 + admin 独立 service 全部就绪，
     * 入参 BO + 返回 VO 与教师端共享（DDL 单源），但 service 方法独立维护。
     *
     * <p>H1 卡 Bug2 补丁：入参 BO 从 {@code QuestionPageBo} → {@link AdminQuestionPageBo}，
     * 新增 {@code tagIds} 支持 admin 列表页多选标签筛选（OR 语义）。
     *
     * <p>响应 envelope：RuoYi 标准 {@code {code:200, msg:"操作成功", data:{records,total,...}}}。
     */
    @SaCheckPermission("admin:question:list")
    @PostMapping("/page")
    public R<MisiktPageVo<QuestionItemVo>> page(@RequestBody(required = false) AdminQuestionPageBo bo) {
        if (bo == null) {
            bo = new AdminQuestionPageBo();
        }
        return R.ok(adminQuestionService.adminPage(bo));
    }

    /**
     * POST /admin/question/lazyTree — 章节-知识点树（admin 通道，独立 service 自查 Mapper）。
     *
     * <p>V0.1 简化（与教师端 /teacher/question/lazyTree 等效）：忽略 parentId 一次返整树；
     * 表只 ~2k 行性能 OK。FE 在 list.vue 章节面板 + edit.vue 知识点选择器复用同端点。
     *
     * <p>响应 envelope：RuoYi 标准 {@code {code:200, msg:"操作成功", data:[嵌套树]}}。
     */
    @SaCheckPermission("admin:question:list")
    @PostMapping("/lazyTree")
    public R<List<SubjectNodeVo>> lazyTree(@RequestBody(required = false) SubjectLazyTreeBo bo) {
        if (bo == null) {
            bo = new SubjectLazyTreeBo();
        }
        return R.ok(adminQuestionService.adminLazyTree(bo));
    }

    /**
     * POST /admin/question/select/{id} — 题目详情（编辑回填用，V-5 波 2a）。
     *
     * <p>响应 envelope：RuoYi 标准 {@code R<QuestionDetailVo>}。
     * 复用教师端 VO（DDL 单源），但 service 方法独立维护。
     *
     * <p>鉴权：{@code admin:question:list}（详情属 list 权限范围；写操作 V-1 才用 edit 权限）。
     */
    @SaCheckPermission("admin:question:list")
    @PostMapping("/select/{id}")
    public R<QuestionDetailVo> select(@PathVariable("id") Long id) {
        return R.ok(adminQuestionService.adminSelectById(id));
    }

    /**
     * POST /admin/question/delete/{id} — 题目软删 + 引用校验（V-2 波 2a）。
     *
     * <p>业务规则（PRD §3.2）：
     * <ul>
     *   <li>biz_paper_question 引用 &gt; 0 → 抛 ServiceException "该题被 N 张试卷引用，无法删除"</li>
     *   <li>未引用 → UPDATE biz_question SET status='2'，软删<strong>不动</strong>
     *       biz_question_knowledge / biz_question_free_tag（历史试卷渲染可用）</li>
     * </ul>
     *
     * <p>响应 envelope：成功 {@code R<Void>.ok()} / 失败 {@code R.fail("...")}（全局异常处理器转）。
     *
     * <p>鉴权：{@code admin:question:edit}（写权限）。
     */
    @SaCheckPermission("admin:question:edit")
    @PostMapping("/delete/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        adminQuestionService.adminSoftDelete(id);
        return R.ok();
    }

    /**
     * POST /admin/question/publish/{id} — 题目发布（状态机 0 → 1，V-3 波 2a）。
     *
     * <p>SQL：{@code UPDATE biz_question SET status='1' WHERE id=? AND status='0'}。
     * 影响行数 0 时抛 ServiceException "状态非法（当前不是草稿，不能发布）"。
     *
     * <p>不允许反向：'1' → '0' / '2' → 任何（PRD §3.8 状态机）。
     *
     * <p>响应 envelope：成功 {@code R<Void>.ok()} / 失败 {@code R.fail("...")}。
     *
     * <p>鉴权：{@code admin:question:edit}（写权限）。
     */
    @SaCheckPermission("admin:question:edit")
    @PostMapping("/publish/{id}")
    public R<Void> publish(@PathVariable("id") Long id) {
        adminQuestionService.adminPublish(id);
        return R.ok();
    }

    /**
     * POST /admin/question/edit — 题目新建 / 编辑统一端点（V-1 + V-6 — 波 2b）。
     *
     * <p>分支（PRD §3.1）：
     * <ul>
     *   <li>{@code body.id == null} → 新建：INSERT biz_question (status='0' 草稿)
     *       + INSERT 知识点 (source='U') + INSERT 标签 + 字典自动建</li>
     *   <li>{@code body.id != null} → 编辑：UPDATE biz_question (status 不动，发布走 publish)
     *       + 全量替换 知识点 (U 轨) + 全量替换 标签</li>
     * </ul>
     *
     * <p>同事务：service 层挂 {@code @Transactional(rollbackFor = Exception.class)}，
     * 任一步抛 {@code ServiceException} 整体回滚。
     *
     * <p>响应：{@code R<Map<"id", Long>>} —— {@code {"code":200,"msg":"操作成功","data":{"id":36187}}}（PRD §3.1）。
     *
     * <p>鉴权：{@code admin:question:edit}（写权限）。
     */
    @SaCheckPermission("admin:question:edit")
    @PostMapping("/edit")
    public R<Map<String, Long>> edit(@RequestBody AdminQuestionEditBo bo) {
        Long id = adminQuestionService.adminEdit(bo);
        return R.ok(Collections.singletonMap("id", id));
    }


    /**
     * POST /admin/question/fileUpload — admin 图上传（multipart → OSS + image_asset，V-4 波 2c）。
     *
     * <p>请求：{@code multipart/form-data}，字段：
     * <ul>
     *   <li>{@code file} — 二进制图（≤ 5MB，PNG / JPG / JPEG / WEBP）必填</li>
     *   <li>{@code type} — stem / answer / explain（可选，default stem，仅用于 image_asset.asset_kind 命名）</li>
     * </ul>
     *
     * <p>实现：详见 {@link IAdminQuestionService#adminUploadFile(MultipartFile, String)}。
     *
     * <p>响应：{@code R<Map<"url", "assetId">>}：
     * <pre>{@code
     * {
     *   "code": 200, "msg": "操作成功",
     *   "data": {
     *     "url": "https://ai-book.oss-cn-hangzhou.aliyuncs.com/admin-upload/2026-05-22/<uuid>.png",
     *     "assetId": 108573
     *   }
     * }
     * }</pre>
     *
     * <p>鉴权：{@code admin:question:edit}（写权限）。
     */
    @SaCheckPermission("admin:question:edit")
    @PostMapping("/fileUpload")
    public R<Map<String, Object>> fileUpload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "type", required = false) String type) {
        return R.ok(adminQuestionService.adminUploadFile(file, type));
    }


    /**
     * POST /admin/question/freeTagSearch — admin freeTag 字典搜索（H1 卡 Bug2 补丁）。
     *
     * <p>用途：FE 编辑页标签改成"搜索式 multi-select"后需此端点提供"输入即搜"
     * 候选下拉；列表页 tag 多选筛选弹层同样复用。
     *
     * <p>入参（{@code application/json}）：
     * <ul>
     *   <li>{@code keyword} — 关键字（可选）；null / 空 / 纯空格 → 返热门 top 20（按 use_count 倒序）</li>
     *   <li>{@code limit} — 返回上限（可选）；null / 非正 → 默认 20；&gt; 100 → clamp 100</li>
     * </ul>
     *
     * <p>响应 envelope：RuoYi 标准 {@code R<List<Map>>}。
     * <pre>{@code
     * {
     *   "code": 200, "msg": "操作成功",
     *   "data": [
     *     {"id": 25, "name": "勾股定理", "useCount": 3200},
     *     {"id": 29, "name": "三角形内角和", "useCount": 1820},
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * <p>鉴权：{@code admin:question:list}（属读端点；列表筛选 + 编辑回填都需访问）。
     */
    @SaCheckPermission("admin:question:list")
    @PostMapping("/freeTagSearch")
    public R<List<Map<String, Object>>> freeTagSearch(@RequestBody(required = false) Map<String, Object> body) {
        String keyword = null;
        Integer limit = null;
        if (body != null) {
            Object kw = body.get("keyword");
            keyword = kw == null ? null : kw.toString();
            Object lm = body.get("limit");
            if (lm instanceof Number) {
                limit = ((Number) lm).intValue();
            } else if (lm != null) {
                try {
                    limit = Integer.parseInt(lm.toString().trim());
                } catch (NumberFormatException ignored) {
                    limit = null;
                }
            }
        }
        return R.ok(adminQuestionService.adminFreeTagSearch(keyword, limit));
    }
}
