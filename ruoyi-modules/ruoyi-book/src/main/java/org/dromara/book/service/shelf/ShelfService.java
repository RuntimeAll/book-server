package org.dromara.book.service.shelf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ShelfBookBo;
import org.dromara.book.domain.bo.ShelfImportBo;
import org.dromara.book.domain.bo.ShelfItemBo;
import org.dromara.book.domain.bo.ShelfNodeBo;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 书架 Service（PRD-002）。书/节点树/内容项一体：CRUD + 整树查询 + 整树建书（import）+ used_count 自增。
 *
 * <p>建模纠偏（D8）：node 树自持，kp_id 仅节点可选标签；书内 override 副本只改本书不动题库原题（D3）。
 * JSON 列以 String 存取，业务层 JsonUtils 序列化（对齐 biz_course_plan_lesson.paper_slots）。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class ShelfService {

    private final BizShelfBookMapper bookMapper;
    private final BizShelfNodeMapper nodeMapper;
    private final BizShelfItemMapper itemMapper;
    private final BizQuestionMapper questionMapper;

    // ───────────────── 书 CRUD + 列表 ─────────────────

    @Transactional(rollbackFor = Exception.class)
    public Long createBook(ShelfBookBo bo) {
        if (bo.getTitle() == null || bo.getTitle().isBlank()) {
            throw new ServiceException("书名 title 不能为空", 400);
        }
        BizShelfBook b = new BizShelfBook();
        b.setTitle(bo.getTitle());
        b.setBookType(bo.getBookType() == null ? "workbook" : bo.getBookType());
        b.setSubjectId(bo.getSubjectId());
        b.setGrade(bo.getGrade());
        b.setEdition(bo.getEdition());
        b.setStatus(bo.getStatus() == null ? "0" : bo.getStatus());
        b.setStyleMetaJson(toJson(bo.getStyleMeta()));
        b.setSourceJobId(bo.getSourceJobId());
        b.setRemark(bo.getRemark());
        b.setOwnerId(LoginHelper.getUserId());
        bookMapper.insert(b);
        return b.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBook(Long id, ShelfBookBo bo) {
        BizShelfBook b = requireOwnedBook(id);
        if (bo.getTitle() != null) b.setTitle(bo.getTitle());
        if (bo.getBookType() != null) b.setBookType(bo.getBookType());
        if (bo.getSubjectId() != null) b.setSubjectId(bo.getSubjectId());
        if (bo.getGrade() != null) b.setGrade(bo.getGrade());
        if (bo.getEdition() != null) b.setEdition(bo.getEdition());
        if (bo.getStatus() != null) b.setStatus(bo.getStatus());
        if (bo.getStyleMeta() != null) b.setStyleMetaJson(toJson(bo.getStyleMeta()));
        if (bo.getRemark() != null) b.setRemark(bo.getRemark());
        bookMapper.updateById(b);
    }

    /** 我的书列表（owner 归属过滤 + type/subject 筛选）→ {rows,total}。 */
    public Map<String, Object> pageBooks(String bookType, String subjectId, String status) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizShelfBook> w = new LambdaQueryWrapper<BizShelfBook>()
            .eq(BizShelfBook::getOwnerId, uid)
            .eq(bookType != null && !bookType.isBlank(), BizShelfBook::getBookType, bookType)
            // 书架列表排除 book_type='special'：专项是 C 线备课栏工作集（走 /teacher/special/list），不是书架书；
            // 除非调用方显式按 special 类型查，否则一律剔除，避免专项泄漏进书架列表（集成回归 FINDING-1）。
            .ne(!"special".equals(bookType), BizShelfBook::getBookType, "special")
            .eq(subjectId != null && !subjectId.isBlank(), BizShelfBook::getSubjectId, subjectId)
            .eq(status != null && !status.isBlank(), BizShelfBook::getStatus, status)
            .orderByDesc(BizShelfBook::getId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizShelfBook b : bookMapper.selectList(w)) {
            rows.add(bookBrief(b, true));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", rows);
        r.put("total", rows.size());
        return r;
    }

    public Map<String, Object> getBook(Long id) {
        return bookBrief(requireOwnedBook(id), true);
    }

    /** 整树查询：书 + 目录节点树（含内容项），一次返回可渲染（PRD scope ①书结构查询）。 */
    public Map<String, Object> getStructure(Long id) {
        BizShelfBook b = requireOwnedBook(id);
        Map<String, Object> m = bookBrief(b, false);

        List<BizShelfNode> nodes = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, id).orderByAsc(BizShelfNode::getSeq).orderByAsc(BizShelfNode::getId));
        List<BizShelfItem> items = itemMapper.selectList(new LambdaQueryWrapper<BizShelfItem>()
            .eq(BizShelfItem::getBookId, id).orderByAsc(BizShelfItem::getSeq).orderByAsc(BizShelfItem::getId));

        // node_id -> item VO 列表
        Map<Long, List<Map<String, Object>>> itemsByNode = new LinkedHashMap<>();
        for (BizShelfItem it : items) {
            itemsByNode.computeIfAbsent(it.getNodeId(), k -> new ArrayList<>()).add(itemVo(it));
        }
        // parent_id -> node VO 列表（保持插入顺序=已按 seq 排）
        Map<Long, List<Map<String, Object>>> childrenByParent = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> nodeVoById = new LinkedHashMap<>();
        for (BizShelfNode n : nodes) {
            Map<String, Object> nv = nodeVo(n);
            nv.put("items", itemsByNode.getOrDefault(n.getId(), new ArrayList<>()));
            nv.put("children", new ArrayList<Map<String, Object>>());
            nodeVoById.put(n.getId(), nv);
            childrenByParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(nv);
        }
        // 挂 children
        for (BizShelfNode n : nodes) {
            List<Map<String, Object>> kids = childrenByParent.get(n.getId());
            if (kids != null) {
                nodeVoById.get(n.getId()).put("children", kids);
            }
        }
        m.put("tree", childrenByParent.getOrDefault(null, new ArrayList<>()));
        return m;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteBook(Long id) {
        requireOwnedBook(id);
        itemMapper.delete(new LambdaQueryWrapper<BizShelfItem>().eq(BizShelfItem::getBookId, id));
        nodeMapper.delete(new LambdaQueryWrapper<BizShelfNode>().eq(BizShelfNode::getBookId, id));
        bookMapper.deleteById(id);
    }

    // ───────────────── 节点 增删改序 ─────────────────

    @Transactional(rollbackFor = Exception.class)
    public Long addNode(ShelfNodeBo bo) {
        if (bo.getBookId() == null) throw new ServiceException("bookId 不能为空", 400);
        requireOwnedBook(bo.getBookId());
        if (bo.getName() == null || bo.getName().isBlank()) throw new ServiceException("节点名 name 不能为空", 400);
        BizShelfNode n = new BizShelfNode();
        n.setBookId(bo.getBookId());
        n.setParentId(bo.getParentId());
        n.setSeq(bo.getSeq() == null ? 0 : bo.getSeq());
        n.setNodeType(bo.getNodeType());
        n.setName(bo.getName());
        n.setKpId(bo.getKpId());
        n.setMetaJson(toJson(bo.getMeta()));
        nodeMapper.insert(n);
        return n.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long id, ShelfNodeBo bo) {
        BizShelfNode n = nodeMapper.selectById(id);
        if (n == null) throw new ServiceException("节点不存在", 404);
        requireOwnedBook(n.getBookId());
        if (bo.getName() != null) n.setName(bo.getName());
        if (bo.getNodeType() != null) n.setNodeType(bo.getNodeType());
        if (bo.getSeq() != null) n.setSeq(bo.getSeq());
        if (bo.getParentId() != null) n.setParentId(bo.getParentId());
        if (bo.getKpId() != null) n.setKpId(bo.getKpId());
        if (bo.getMeta() != null) n.setMetaJson(toJson(bo.getMeta()));
        nodeMapper.updateById(n);
    }

    /** 删节点：级联子树 + 子树下所有内容项。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long id) {
        BizShelfNode n = nodeMapper.selectById(id);
        if (n == null) return;
        requireOwnedBook(n.getBookId());
        List<Long> subtree = collectSubtree(n.getBookId(), id);
        itemMapper.delete(new LambdaQueryWrapper<BizShelfItem>().in(BizShelfItem::getNodeId, subtree));
        nodeMapper.delete(new LambdaQueryWrapper<BizShelfNode>().in(BizShelfNode::getId, subtree));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderNodes(List<Long> ids) {
        if (ids == null) return;
        int seq = 1;
        for (Long nid : ids) {
            BizShelfNode n = nodeMapper.selectById(nid);
            if (n != null) {
                requireOwnedBook(n.getBookId());
                n.setSeq(seq);
                nodeMapper.updateById(n);
            }
            seq++;
        }
    }

    // ───────────────── 内容项 增删改序 + override + used_count ─────────────────

    @Transactional(rollbackFor = Exception.class)
    public Long addItem(ShelfItemBo bo) {
        if (bo.getNodeId() == null) throw new ServiceException("nodeId 不能为空", 400);
        BizShelfNode n = nodeMapper.selectById(bo.getNodeId());
        if (n == null) throw new ServiceException("节点不存在", 404);
        requireOwnedBook(n.getBookId());
        BizShelfItem it = new BizShelfItem();
        it.setBookId(n.getBookId());
        it.setNodeId(bo.getNodeId());
        it.setSeq(bo.getSeq() == null ? 0 : bo.getSeq());
        Long qid = parseId(bo.getQuestionId());
        it.setKind(bo.getKind() != null ? bo.getKind() : (qid != null ? "question" : "explain"));
        it.setQuestionId(qid);
        if (qid != null) {
            validateQuestionsExist(List.of(qid));   // 防空壳引用（finding 4：不存在的 qid 静默入库）
        }
        it.setOverrideJson(toJson(bo.getOverride()));
        it.setExplainJson(toJson(bo.getExplain()));
        it.setUsedCount(0);
        itemMapper.insert(it);
        return it.getId();
    }

    /** 更新内容项：seq/kind/questionId/override_json/explain_json（override 写入走此，D3）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, ShelfItemBo bo) {
        BizShelfItem it = itemMapper.selectById(id);
        if (it == null) throw new ServiceException("内容项不存在", 404);
        requireOwnedBook(it.getBookId());
        if (bo.getSeq() != null) it.setSeq(bo.getSeq());
        if (bo.getKind() != null) it.setKind(bo.getKind());
        if (bo.getQuestionId() != null) it.setQuestionId(parseId(bo.getQuestionId()));
        if (bo.getOverride() != null) it.setOverrideJson(toJson(bo.getOverride()));
        if (bo.getExplain() != null) it.setExplainJson(toJson(bo.getExplain()));
        itemMapper.updateById(it);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        BizShelfItem it = itemMapper.selectById(id);
        if (it == null) return;
        requireOwnedBook(it.getBookId());
        itemMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reorderItems(List<Long> ids) {
        if (ids == null) return;
        int seq = 1;
        for (Long iid : ids) {
            BizShelfItem it = itemMapper.selectById(iid);
            if (it != null) {
                requireOwnedBook(it.getBookId());
                it.setSeq(seq);
                itemMapper.updateById(it);
            }
            seq++;
        }
    }

    /** used_count +1（上课认证；PRD-003 导出回写通路）。返回自增后的计数。 */
    @Transactional(rollbackFor = Exception.class)
    public int incrUsed(Long id) {
        BizShelfItem it = itemMapper.selectById(id);
        if (it == null) throw new ServiceException("内容项不存在", 404);
        requireOwnedBook(it.getBookId());
        int next = (it.getUsedCount() == null ? 0 : it.getUsedCount()) + 1;
        it.setUsedCount(next);
        itemMapper.updateById(it);
        return next;
    }

    // ───────────────── 整树一次建书 import（直出书交接面，契约§3） ─────────────────

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importBook(ShelfImportBo bo) {
        if (bo.getTitle() == null || bo.getTitle().isBlank()) {
            throw new ServiceException("书名 title 不能为空", 400);
        }
        // 防御闸：payload 缺 tree（如字段名写错为 nodes）或 tree 非数组/空数组 → 拒绝，不再静默建空壳书
        if (bo.getTree() == null || bo.getTree().isEmpty()) {
            throw new ServiceException("整树 tree 缺失或非数组：拒绝建空书（请按 schema 传非空 tree 数组）", 400);
        }
        // finding 4：整树建书前批量校验所有题引用存在（防 B 线程序化直出书塞空壳引用，事务级 fail-fast）
        Set<Long> allQids = new LinkedHashSet<>();
        if (bo.getTree() != null) {
            for (ShelfImportBo.ImportNode node : bo.getTree()) {
                collectQids(node, allQids);
            }
        }
        validateQuestionsExist(allQids);

        BizShelfBook b = new BizShelfBook();
        b.setTitle(bo.getTitle());
        b.setBookType(bo.getBookType() == null ? "workbook" : bo.getBookType());
        b.setSubjectId(bo.getSubjectId());
        b.setGrade(bo.getGrade());
        b.setEdition(bo.getEdition());
        b.setStatus("0");
        b.setStyleMetaJson(toJson(bo.getStyleMeta()));
        b.setSourceJobId(bo.getSourceJobId());
        b.setOwnerId(LoginHelper.getUserId());
        bookMapper.insert(b);

        int[] counters = new int[]{0, 0}; // [nodeCount, itemCount]
        if (bo.getTree() != null) {
            int seq = 1;
            for (ShelfImportBo.ImportNode node : bo.getTree()) {
                importNode(b.getId(), null, node, seq++, counters);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(b.getId()));
        r.put("nodeCount", counters[0]);
        r.put("itemCount", counters[1]);
        return r;
    }

    private void importNode(Long bookId, Long parentId, ShelfImportBo.ImportNode node, int seq, int[] counters) {
        BizShelfNode n = new BizShelfNode();
        n.setBookId(bookId);
        n.setParentId(parentId);
        n.setSeq(seq);
        n.setNodeType(node.getNodeType());
        n.setName(node.getName() == null ? "" : node.getName());
        n.setKpId(node.getKpId());
        n.setMetaJson(toJson(node.getMeta()));
        nodeMapper.insert(n);
        counters[0]++;

        if (node.getItems() != null) {
            int iseq = 1;
            for (ShelfImportBo.ImportItem item : node.getItems()) {
                BizShelfItem it = new BizShelfItem();
                it.setBookId(bookId);
                it.setNodeId(n.getId());
                it.setSeq(iseq++);
                Long qid = parseId(item.getQuestionId());
                it.setKind(item.getKind() != null ? item.getKind() : (qid != null ? "question" : "explain"));
                it.setQuestionId(qid);
                it.setOverrideJson(toJson(item.getOverride()));
                it.setExplainJson(toJson(item.getExplain()));
                it.setUsedCount(0);
                itemMapper.insert(it);
                counters[1]++;
            }
        }
        if (node.getChildren() != null) {
            int cseq = 1;
            for (ShelfImportBo.ImportNode child : node.getChildren()) {
                importNode(bookId, n.getId(), child, cseq++, counters);
            }
        }
    }

    // ───────────────── helpers ─────────────────

    /** 递归收集整树里所有 kind=question 的 questionId（非空）。 */
    private void collectQids(ShelfImportBo.ImportNode node, Set<Long> out) {
        if (node == null) return;
        if (node.getItems() != null) {
            for (ShelfImportBo.ImportItem item : node.getItems()) {
                Long qid = parseId(item.getQuestionId());
                boolean isQuestion = "question".equals(item.getKind()) || (item.getKind() == null && qid != null);
                if (isQuestion && qid != null) out.add(qid);
            }
        }
        if (node.getChildren() != null) {
            for (ShelfImportBo.ImportNode child : node.getChildren()) collectQids(child, out);
        }
    }

    /**
     * 题引用存在性校验（finding 4）：任一 questionId 在 biz_question 查不到即 400，
     * 阻断"不存在的 qid 静默入库成空壳引用"（同 create_paper 已知半坑）。
     * biz_question 无 tenant_id/data-scope 语义，线程级 ignore 兜底（对齐 QuestionServiceImpl）。
     */
    private void validateQuestionsExist(Collection<Long> qids) {
        if (qids == null || qids.isEmpty()) return;
        Set<Long> distinct = new LinkedHashSet<>(qids);
        List<BizQuestion> found = TenantHelper.ignore(() -> DataPermissionHelper.ignore(() ->
            questionMapper.selectBatchIds(distinct)));
        Set<Long> foundIds = found == null ? Set.of()
            : found.stream().map(BizQuestion::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new ServiceException("题引用不存在（biz_question 无此题）: " + missing, 400);
        }
    }

    /** 归属校验：书必须存在且属当前登录老师，否则 404（水平越权兜底）。 */
    private BizShelfBook requireOwnedBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        Long uid = LoginHelper.getUserId();
        if (uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId())) {
            throw new ServiceException("无权访问该书", 403);
        }
        return b;
    }

    /** 收集节点子树 id（含自身），用于级联删除。 */
    private List<Long> collectSubtree(Long bookId, Long rootId) {
        List<BizShelfNode> all = nodeMapper.selectList(new LambdaQueryWrapper<BizShelfNode>()
            .eq(BizShelfNode::getBookId, bookId));
        Map<Long, List<Long>> childrenOf = new LinkedHashMap<>();
        for (BizShelfNode n : all) {
            childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n.getId());
        }
        List<Long> result = new ArrayList<>();
        List<Long> stack = new ArrayList<>();
        stack.add(rootId);
        while (!stack.isEmpty()) {
            Long cur = stack.remove(stack.size() - 1);
            result.add(cur);
            List<Long> kids = childrenOf.get(cur);
            if (kids != null) stack.addAll(kids);
        }
        return result;
    }

    private Map<String, Object> bookBrief(BizShelfBook b, boolean withStats) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(b.getId()));
        m.put("bookType", b.getBookType());
        m.put("title", b.getTitle());
        m.put("subjectId", b.getSubjectId());
        m.put("grade", b.getGrade());
        m.put("edition", b.getEdition());
        m.put("ownerId", b.getOwnerId() == null ? null : String.valueOf(b.getOwnerId()));
        m.put("status", b.getStatus());
        m.put("styleMeta", parse(b.getStyleMetaJson()));
        m.put("sourceJobId", b.getSourceJobId() == null ? null : String.valueOf(b.getSourceJobId()));
        m.put("remark", b.getRemark());
        m.put("createTime", b.getCreateTime());
        m.put("updateTime", b.getUpdateTime());
        if (withStats) {
            long nodeCount = nodeMapper.selectCount(new LambdaQueryWrapper<BizShelfNode>()
                .eq(BizShelfNode::getBookId, b.getId()));
            long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<BizShelfItem>()
                .eq(BizShelfItem::getBookId, b.getId()));
            long questionCount = itemMapper.selectCount(new LambdaQueryWrapper<BizShelfItem>()
                .eq(BizShelfItem::getBookId, b.getId()).eq(BizShelfItem::getKind, "question"));
            m.put("nodeCount", nodeCount);
            m.put("itemCount", itemCount);
            m.put("questionCount", questionCount);
        }
        return m;
    }

    private Map<String, Object> nodeVo(BizShelfNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(n.getId()));
        m.put("bookId", String.valueOf(n.getBookId()));
        m.put("parentId", n.getParentId() == null ? null : String.valueOf(n.getParentId()));
        m.put("seq", n.getSeq());
        m.put("nodeType", n.getNodeType());
        m.put("name", n.getName());
        m.put("kpId", n.getKpId() == null ? null : String.valueOf(n.getKpId()));
        m.put("meta", parse(n.getMetaJson()));
        return m;
    }

    private Map<String, Object> itemVo(BizShelfItem it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(it.getId()));
        m.put("bookId", String.valueOf(it.getBookId()));
        m.put("nodeId", String.valueOf(it.getNodeId()));
        m.put("seq", it.getSeq());
        m.put("kind", it.getKind());
        m.put("questionId", it.getQuestionId() == null ? null : String.valueOf(it.getQuestionId()));
        m.put("override", parse(it.getOverrideJson()));
        m.put("explain", parse(it.getExplainJson()));
        m.put("usedCount", it.getUsedCount());
        return m;
    }

    private String toJson(Object o) {
        if (o == null) return null;
        if (o instanceof String s) {
            return s.isBlank() ? null : s;
        }
        return JsonUtils.toJsonString(o);
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonUtils.parseObject(json, Object.class);
    }

    private Long parseId(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("id 非法：" + s, 400);
        }
    }
}
