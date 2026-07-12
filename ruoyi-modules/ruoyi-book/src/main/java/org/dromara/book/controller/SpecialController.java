package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.service.SpecialService;
import org.dromara.book.service.SpecialExportService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专项（book_type='special'）编排 Controller（PRD-003，跨线契约 §4 C 位独占面）。
 *
 * <p>🔴 必须落 {@code org.dromara.book.controller} 包 —— MisiktEnvelopeAdvice 按 basePackages 装配，
 * /teacher/ 前缀响应转 misikt envelope {code:1,message,response}。
 *
 * <p>路由：专项 CRUD + 节点(sec/tier)增删改序 + 题目增删改/拖排 + 冻结面 pick + 材料位(special_ids)。
 *
 * @author codeplace-C PRD-003
 */
@RestController
@RequestMapping("/teacher/special")
@RequiredArgsConstructor
public class SpecialController {

    private final SpecialService specialService;
    private final SpecialExportService specialExportService;

    // ───────── 专项书 CRUD ─────────

    @SaCheckLogin
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long id = specialService.createSpecial(body);
        return R.ok(idMap(id));
    }

    /** 我的专项列表（备课栏）。 */
    @SaCheckLogin
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(specialService.listMine());
    }

    @SaCheckLogin
    @GetMapping("/{specialId}")
    public R<Map<String, Object>> detail(@PathVariable Long specialId) {
        return R.ok(specialService.detail(specialId));
    }

    @SaCheckLogin
    @PutMapping("/{specialId}")
    public R<Void> update(@PathVariable Long specialId, @RequestBody Map<String, Object> body) {
        specialService.updateBook(specialId, body);
        return R.ok();
    }

    @SaCheckLogin
    @DeleteMapping("/{specialId}")
    public R<Void> delete(@PathVariable Long specialId) {
        specialService.deleteSpecial(specialId);
        return R.ok();
    }

    // ───────── 节点（sec/tier）增删改序 ─────────

    @SaCheckLogin
    @PostMapping("/{specialId}/node")
    public R<Map<String, Object>> createNode(@PathVariable Long specialId, @RequestBody Map<String, Object> body) {
        Long id = specialService.createNode(specialId, body);
        return R.ok(idMap(id));
    }

    @SaCheckLogin
    @PutMapping("/node/{nodeId}")
    public R<Void> updateNode(@PathVariable Long nodeId, @RequestBody Map<String, Object> body) {
        specialService.updateNode(nodeId, body);
        return R.ok();
    }

    @SaCheckLogin
    @DeleteMapping("/node/{nodeId}")
    public R<Void> deleteNode(@PathVariable Long nodeId) {
        specialService.deleteNode(nodeId);
        return R.ok();
    }

    /** 同层节点重排。body {nodeIds:[...]}。 */
    @SaCheckLogin
    @PutMapping("/{specialId}/nodes/reorder")
    public R<Void> reorderNodes(@PathVariable Long specialId, @RequestBody Map<String, Object> body) {
        specialService.reorderNodes(specialId, longList(body.get("nodeIds")));
        return R.ok();
    }

    // ───────── 题目（item）增删改 / 拖排 ─────────

    @SaCheckLogin
    @PostMapping("/node/{nodeId}/item")
    public R<Map<String, Object>> addItem(@PathVariable Long nodeId, @RequestBody Map<String, Object> body) {
        Long id = specialService.addItem(nodeId, body);
        return R.ok(idMap(id));
    }

    @SaCheckLogin
    @PutMapping("/item/{itemId}")
    public R<Void> updateItem(@PathVariable Long itemId, @RequestBody Map<String, Object> body) {
        specialService.updateItem(itemId, body);
        return R.ok();
    }

    @SaCheckLogin
    @DeleteMapping("/item/{itemId}")
    public R<Void> deleteItem(@PathVariable Long itemId) {
        specialService.deleteItem(itemId);
        return R.ok();
    }

    /** 节点内题目拖排。body {itemIds:[...]}。 */
    @SaCheckLogin
    @PutMapping("/node/{nodeId}/items/reorder")
    public R<Void> reorderItems(@PathVariable Long nodeId, @RequestBody Map<String, Object> body) {
        specialService.reorderItems(nodeId, longList(body.get("itemIds")));
        return R.ok();
    }

    // ───────── 冻结面 pick（契约 §3） ─────────

    /** 挑题入专项（冻结面）。body {questionId?|nodeId?, overrideJson?, secHint?}。 */
    @SaCheckLogin
    @PostMapping("/{specialId}/pick")
    public R<Map<String, Object>> pick(@PathVariable Long specialId, @RequestBody Map<String, Object> body) {
        return R.ok(specialService.pick(specialId, body));
    }

    // ───────── 双卷 PDF 导出（P5 / D3） ─────────

    /**
     * 导出专项双卷 PDF（题目卷 / 答案卷）。body {papers, withAnalysis, withStars}。
     * 返回 {specialId, questionUrl?, answerUrl?, markedCount}；导出即对涉及 item used_count+1。
     */
    @SaCheckLogin
    @PostMapping("/{specialId}/export")
    public R<Map<String, Object>> export(@PathVariable Long specialId, @RequestBody Map<String, Object> body) {
        return R.ok(specialExportService.export(specialId, body));
    }

    // ───────── 材料位（special_ids 单列） ─────────

    /** 课次绑专项。body {specialId}。 */
    @SaCheckLogin
    @PostMapping("/lesson/{lessonId}/bind")
    public R<Map<String, Object>> bindLesson(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        Long specialId = reqLong(body.get("specialId"), "specialId");
        List<String> ids = specialService.bindLesson(lessonId, specialId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("specialIds", ids);
        return R.ok(r);
    }

    /** 课次解绑专项。body {specialId}。 */
    @SaCheckLogin
    @PostMapping("/lesson/{lessonId}/unbind")
    public R<Map<String, Object>> unbindLesson(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        Long specialId = reqLong(body.get("specialId"), "specialId");
        List<String> ids = specialService.unbindLesson(lessonId, specialId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("specialIds", ids);
        return R.ok(r);
    }

    /** 查询课次材料（已绑专项概要）。 */
    @SaCheckLogin
    @GetMapping("/lesson/{lessonId}/materials")
    public R<Map<String, Object>> materials(@PathVariable Long lessonId) {
        return R.ok(specialService.lessonMaterials(lessonId));
    }

    // ───────── helpers ─────────

    private Map<String, Object> idMap(Long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(id));
        return m;
    }

    private Long reqLong(Object o, String field) {
        if (o == null || String.valueOf(o).isBlank()) {
            throw new ServiceException(field + " 不能为空", 400);
        }
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException(field + " 非法：" + o, 400);
        }
    }

    private List<Long> longList(Object o) {
        List<Long> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x == null) continue;
                try {
                    out.add(Long.valueOf(String.valueOf(x).trim()));
                } catch (NumberFormatException e) {
                    throw new ServiceException("非法 id：" + x, 400);
                }
            }
        }
        return out;
    }
}
