package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ShelfBookBo;
import org.dromara.book.domain.bo.ShelfImportBo;
import org.dromara.book.domain.bo.ShelfItemBo;
import org.dromara.book.domain.bo.ShelfNodeBo;
import org.dromara.book.service.shelf.ShelfService;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @SaCheckLogin
    @DeleteMapping("/book/{id}")
    public R<Void> deleteBook(@PathVariable Long id) {
        shelfService.deleteBook(id);
        return R.ok();
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

    // ───────────────── 整树一次建书（直出书交接面，契约§3） ─────────────────

    @SaCheckLogin
    @PostMapping("/import")
    public R<Map<String, Object>> importBook(@RequestBody ShelfImportBo bo) {
        return R.ok(shelfService.importBook(bo));
    }
}
