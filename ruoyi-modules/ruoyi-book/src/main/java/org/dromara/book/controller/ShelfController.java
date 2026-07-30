package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ShelfBookBo;
import org.dromara.book.domain.bo.ShelfImportBo;
import org.dromara.book.domain.bo.ShelfItemBo;
import org.dromara.book.domain.bo.ShelfNodeBo;
import org.dromara.book.service.shelf.BookExportService;
import org.dromara.book.service.shelf.ShelfPdfImportService;
import org.dromara.book.service.shelf.ShelfService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书架 Controller（PRD-002 /teacher/shelf）。命中 MisiktEnvelopeAdvice → {code:1,message,response}。
 *
 * <p>讲义/练习册/专项统一书实体的 CRUD + 整树查询 + 整树建书 import + 节点/内容项增删改序 + used_count 自增。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/shelf")
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;
    private final BookExportService bookExportService;
    private final ShelfPdfImportService shelfPdfImportService;

    // ───────────────── 书 ─────────────────

    @SaCheckLogin
    @PostMapping("/book")
    public R<Map<String, Object>> createBook(@RequestBody ShelfBookBo bo) {
        Long id = shelfService.createBook(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    @SaCheckLogin
    @PutMapping("/book/{id}")
    public R<Void> updateBook(@PathVariable Long id, @RequestBody ShelfBookBo bo) {
        shelfService.updateBook(id, bo);
        return R.ok();
    }

    /** 我的书列表（owner 归属过滤 + type/subject/status 筛选）。 */
    @SaCheckLogin
    @GetMapping("/book/page")
    public R<Map<String, Object>> pageBooks(@RequestParam(required = false) String bookType,
                                            @RequestParam(required = false) String subjectId,
                                            @RequestParam(required = false) String status) {
        return R.ok(shelfService.pageBooks(bookType, subjectId, status));
    }

    @SaCheckLogin
    @GetMapping("/book/{id}")
    public R<Map<String, Object>> getBook(@PathVariable Long id) {
        return R.ok(shelfService.getBook(id));
    }

    /** 书结构整树（目录树 + 节点内容项，一次返回可渲染）。 */
    @SaCheckLogin
    @GetMapping("/book/{id}/structure")
    public R<Map<String, Object>> structure(@PathVariable Long id) {
        return R.ok(shelfService.getStructure(id));
    }

    /**
     * 电子课本阅读页数据（2026-07-30 课本展示改版）：目录（章+起始页码）+ 全书页表（页码→整页图）。
     * 零迁移——页图取自题块，页码=source_page。返回 {title,totalPages,chapters,pages}。
     */
    @SaCheckLogin
    @GetMapping("/book/{id}/pages")
    public R<Map<String, Object>> textbookPages(@PathVariable Long id) {
        return R.ok(bookExportService.textbookPages(id));
    }

    @SaCheckLogin
    @DeleteMapping("/book/{id}")
    public R<Void> deleteBook(@PathVariable Long id) {
        shelfService.deleteBook(id);
        return R.ok();
    }

    /**
     * 绑定书的网盘链接（整表覆盖保存，零 DDL 落 style_meta_json.netdisks）。
     * body: {@code {netdisks:[{provider,url,code?,note?}]}}，provider ∈ baidu/quark/aliyun/other，
     * 空数组=清空，上限 10 条。🔴 只覆盖 netdisks 键，style_meta 其余键原样保留。
     * 返回 {bookId, netdisks}；书详情/列表的 styleMeta 里天然带出。
     */
    @SaCheckLogin
    @PostMapping("/book/{id}/netdisks")
    public R<Map<String, Object>> saveNetdisks(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("netdisks");
        if (raw != null && !(raw instanceof List)) {
            throw new ServiceException("netdisks 必须是数组", 400);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> netdisks = (List<Map<String, Object>>) raw;
        return R.ok(shelfService.saveNetdisks(id, netdisks));
    }

    /**
     * PDF 直录待解析书（轻挂载）。multipart：file 必填、title 必填、grade/subjectId 选填。
     * 原件进 OSS + 第 1 页渲封面 PNG + 建 {@code book_type=pdf_pending} 空壳书。
     * 返回 {id, title, bookType, pdfUrl, pdfPages, coverUrl}。
     */
    @SaCheckLogin
    @PostMapping("/book/importPdf")
    public R<Map<String, Object>> importPdf(@RequestParam(value = "file", required = false) MultipartFile file,
                                            // 🔴 required=false 是故意的：交给 Service 出人话 400，
                                            //    否则 Spring 的 MissingServletRequestParameter 会被兜成 500「未知异常」
                                            @RequestParam(value = "title", required = false) String title,
                                            @RequestParam(value = "grade", required = false) String grade,
                                            @RequestParam(value = "subjectId", required = false) String subjectId,
                                            @RequestParam(value = "edition", required = false) String edition,
                                            @RequestParam(value = "unit", required = false) String unit) {
        return R.ok(shelfPdfImportService.importPdf(file, title, grade, subjectId, edition, unit));
    }

    // ───────────────── 节点 ─────────────────

    @SaCheckLogin
    @PostMapping("/node")
    public R<Map<String, Object>> addNode(@RequestBody ShelfNodeBo bo) {
        Long id = shelfService.addNode(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    @SaCheckLogin
    @PutMapping("/node/{id}")
    public R<Void> updateNode(@PathVariable Long id, @RequestBody ShelfNodeBo bo) {
        shelfService.updateNode(id, bo);
        return R.ok();
    }

    @SaCheckLogin
    @DeleteMapping("/node/{id}")
    public R<Void> deleteNode(@PathVariable Long id) {
        shelfService.deleteNode(id);
        return R.ok();
    }

    /** 同层节点重排。body: {@code {"ids":[...]}}。 */
    @SaCheckLogin
    @PostMapping("/node/reorder")
    public R<Void> reorderNodes(@RequestBody Map<String, List<Long>> body) {
        shelfService.reorderNodes(body == null ? null : body.get("ids"));
        return R.ok();
    }

    // ───────────────── 内容项 ─────────────────

    @SaCheckLogin
    @PostMapping("/item")
    public R<Map<String, Object>> addItem(@RequestBody ShelfItemBo bo) {
        Long id = shelfService.addItem(bo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", String.valueOf(id));
        return R.ok(r);
    }

    /** 更新内容项（含 override_json 写入，D3）。 */
    @SaCheckLogin
    @PutMapping("/item/{id}")
    public R<Void> updateItem(@PathVariable Long id, @RequestBody ShelfItemBo bo) {
        shelfService.updateItem(id, bo);
        return R.ok();
    }

    @SaCheckLogin
    @DeleteMapping("/item/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        shelfService.deleteItem(id);
        return R.ok();
    }

    /** 同节点内容项重排。body: {@code {"ids":[...]}}。 */
    @SaCheckLogin
    @PostMapping("/item/reorder")
    public R<Void> reorderItems(@RequestBody Map<String, List<Long>> body) {
        shelfService.reorderItems(body == null ? null : body.get("ids"));
        return R.ok();
    }

    /** used_count +1（上课认证；PRD-003 导出回写通路）。返回 {usedCount}。 */
    @SaCheckLogin
    @PostMapping("/item/{id}/used")
    public R<Map<String, Object>> incrUsed(@PathVariable Long id) {
        int next = shelfService.incrUsed(id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("usedCount", next);
        return R.ok(r);
    }

    // ───────────────── 课次 ↔ 书章节材料位（book_node_ids 单列，复刻 special 材料位范式） ─────────────────

    /** 课次绑书章节。body: {@code {"nodeId":"..."}}。🔴 BE 只 UPDATE book_node_ids 单列。 */
    @SaCheckLogin
    @PostMapping("/lesson/{lessonId}/bind-node")
    public R<Map<String, Object>> bindLessonNode(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        Long nodeId = idOf(body == null ? null : body.get("nodeId"));
        List<String> ids = shelfService.bindLessonNode(lessonId, nodeId);
        return R.ok(lessonNodeIdsVo(lessonId, ids));
    }

    /** 课次解绑书章节。body: {@code {"nodeId":"..."}}。 */
    @SaCheckLogin
    @PostMapping("/lesson/{lessonId}/unbind-node")
    public R<Map<String, Object>> unbindLessonNode(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        Long nodeId = idOf(body == null ? null : body.get("nodeId"));
        List<String> ids = shelfService.unbindLessonNode(lessonId, nodeId);
        return R.ok(lessonNodeIdsVo(lessonId, ids));
    }

    /** 查询课次已绑书章节材料 → {lessonId, bookNodeIds, materials:[{nodeId,nodeTitle,bookId,bookTitle,questionCount}]}。 */
    @SaCheckLogin
    @GetMapping("/lesson/{lessonId}/book-materials")
    public R<Map<String, Object>> lessonBookMaterials(@PathVariable Long lessonId) {
        return R.ok(shelfService.lessonBookMaterials(lessonId));
    }

    private Map<String, Object> lessonNodeIdsVo(Long lessonId, List<String> ids) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lessonId", String.valueOf(lessonId));
        r.put("bookNodeIds", ids);
        return r;
    }

    private Long idOf(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ───────────────── 书导出 PDF ─────────────────

    /**
     * 整书导出 PDF。body: {@code {withAnswers?:boolean}}。
     * 讲义/练习册书=分讲 HTML→Chrome→PDF+pdfbox 合并；图片书(textbook)=整页图逐页拼 A4。
     * 返回 {@code {url, pages?}}（OSS）。鉴权=登录 + book owner 或超管。
     */
    @SaCheckLogin
    @PostMapping("/book/{bookId}/export")
    public R<Map<String, Object>> exportBook(@PathVariable Long bookId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(bookExportService.export(bookId, body == null ? new LinkedHashMap<>() : body));
    }

    // ───────────────── 整树一次建书（直出书交接面，契约§3） ─────────────────

    @SaCheckLogin
    @PostMapping("/import")
    public R<Map<String, Object>> importBook(@RequestBody ShelfImportBo bo) {
        return R.ok(shelfService.importBook(bo));
    }
}
