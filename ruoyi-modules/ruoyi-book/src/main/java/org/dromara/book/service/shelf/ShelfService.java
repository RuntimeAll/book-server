package org.dromara.book.service.shelf;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.ShelfBookBo;
import org.dromara.book.domain.bo.ShelfImportBo;
import org.dromara.book.domain.bo.ShelfItemBo;
import org.dromara.book.domain.bo.ShelfNodeBo;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizShelfBook;
import org.dromara.book.domain.entity.BizShelfItem;
import org.dromara.book.domain.entity.BizShelfNode;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizShelfBookMapper;
import org.dromara.book.mapper.BizShelfItemMapper;
import org.dromara.book.mapper.BizShelfNodeMapper;
import org.dromara.book.mapper.BizSubjectMapper;
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
    private final BizSubjectMapper subjectMapper;
    private final BizCoursePlanLessonMapper lessonMapper;

    /**
     * PDF 直录待解析书类型（与电子课本 textbook 区分：只挂原件+封面，没有节点树/题项，
     * 等人工或管线来解析）。book_type 列 varchar(16)，无字典无枚举约束，同 daily_punch 加法。
     */
    public static final String BOOK_TYPE_PDF_PENDING = "pdf_pending";

    /** 网盘链接 provider 白名单（style_meta_json.netdisks[].provider）。 */
    private static final Set<String> NETDISK_PROVIDERS = Set.of("baidu", "quark", "aliyun", "other");

    /** 一本书最多绑几条网盘链接。 */
    private static final int NETDISK_MAX = 10;

    /** 宣发文案长度上限（style_meta_json.promo）。 */
    private static final int PROMO_TITLE_MAX = 100;
    private static final int PROMO_DESC_MAX = 3000;

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
        // 🔴 公开标记：仅超管可改（与题目 setPublic 同口径——「入库 ≠ 公开」）。
        //    老师传了也不生效，直接拒，避免静默忽略造成「我明明改了」的误判。
        if (bo.getIsPublic() != null) {
            if (!LoginHelper.isSuperAdmin()) {
                throw new ServiceException("公开/取消公开仅超级管理员可操作", 403);
            }
            b.setIsPublic(bo.getIsPublic() != 0 ? 1 : 0);
        }
        if (bo.getStyleMeta() != null) b.setStyleMetaJson(toJson(bo.getStyleMeta()));
        if (bo.getRemark() != null) b.setRemark(bo.getRemark());
        bookMapper.updateById(b);
    }

    /** 我的书列表（owner 归属过滤 + type/subject 筛选）→ {rows,total}。 */
    public Map<String, Object> pageBooks(String bookType, String subjectId, String status) {
        Long uid = LoginHelper.getUserId();
        LambdaQueryWrapper<BizShelfBook> w = new LambdaQueryWrapper<BizShelfBook>()
            .and(q -> q.eq(BizShelfBook::getOwnerId, uid).or().eq(BizShelfBook::getIsPublic, 1))
            .eq(bookType != null && !bookType.isBlank(), BizShelfBook::getBookType, bookType)
            // 书架列表排除 book_type='special'：专项是 C 线备课栏工作集（走 /teacher/special/list），不是书架书；
            // 除非调用方显式按 special 类型查，否则一律剔除，避免专项泄漏进书架列表（集成回归 FINDING-1）。
            .ne(!"special".equals(bookType), BizShelfBook::getBookType, "special")
            .eq(subjectId != null && !subjectId.isBlank(), BizShelfBook::getSubjectId, subjectId)
            // 🔴 status 缺省=只列正常书（'0'）——归档书（'1'）不进书架列表（verifier B6：
            //    G-TEST 归档书曾照常列出）；要看归档显式传 status='1'。
            .eq(BizShelfBook::getStatus, (status != null && !status.isBlank()) ? status : "0")
            .orderByDesc(BizShelfBook::getId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizShelfBook b : bookMapper.selectList(w)) {
            rows.add(bookBrief(b, true));
        }
        enrichSubjectDims(rows);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rows", rows);
        r.put("total", rows.size());
        return r;
    }

    /**
     * 书架筛选维度回填（对齐卷库筛选）：书 subject_id → biz_subject 结构列
     * （subject/stage/grade/volume/edition 枚举码），供前端与卷库同构地按学科/学段/年级/册筛选。
     *
     * <p>只在列表页附加 5 个结构码字段（*Code），不改书自持的 grade/edition 文本列，不破坏既有契约。
     * subject_id 未挂 biz_subject 或该 biz_subject 结构列为空的书（多为未分类科学书），对应码留 null，
     * 只在「全部」维度下出现——诚实反映数据现状，不臆造归类。
     */
    private void enrichSubjectDims(List<Map<String, Object>> rows) {
        Set<String> subjectIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object sid = row.get("subjectId");
            if (sid != null && !String.valueOf(sid).isBlank()) subjectIds.add(String.valueOf(sid));
        }
        Map<String, BizSubject> subjMap = new LinkedHashMap<>();
        if (!subjectIds.isEmpty()) {
            List<BizSubject> subs = subjectMapper.selectBatchIds(subjectIds);
            if (subs != null) {
                for (BizSubject s : subs) subjMap.put(s.getId(), s);
            }
        }
        for (Map<String, Object> row : rows) {
            Object sid = row.get("subjectId");
            BizSubject s = sid == null ? null : subjMap.get(String.valueOf(sid));
            row.put("subjectCode", s == null ? null : s.getSubject());
            row.put("stageCode", s == null ? null : s.getStage());
            row.put("gradeCode", s == null ? null : s.getGrade());
            row.put("volumeCode", s == null ? null : s.getVolume());
            row.put("editionCode", s == null ? null : s.getEdition());
        }
    }

    public Map<String, Object> getBook(Long id) {
        return bookBrief(requireReadableBook(id), true);
    }

    /** 整树查询：书 + 目录节点树（含内容项），一次返回可渲染（PRD scope ①书结构查询）。 */
    public Map<String, Object> getStructure(Long id) {
        BizShelfBook b = requireReadableBook(id);
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

    // ───────────────── 网盘链接（style_meta_json.netdisks，零 DDL） ─────────────────

    /**
     * 绑定书的网盘链接（整表覆盖式保存：传什么就是什么，空数组=清空）。
     *
     * <p>🔴 合并写：只覆盖 style_meta_json 的 {@code netdisks} 键，
     * accent/subtitle/variantName/punchReview 等既有键原样保留（同
     * {@code PunchService.recomputeBookReview} 的 style_meta 读-改-写范式），
     * 且只 UPDATE style_meta_json 一列，不整行 upsert。
     *
     * @param bookId 书 id（写门禁 = {@link #requireOwnedBook}）
     * @param raw    {@code [{provider,url,code?,note?}]}；provider ∈ baidu/quark/aliyun/other，url 非空，≤10 条
     * @return {bookId, netdisks}
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveNetdisks(Long bookId, List<Map<String, Object>> raw) {
        BizShelfBook b = requireOwnedBook(bookId);

        List<Map<String, Object>> netdisks = new ArrayList<>();
        if (raw != null) {
            if (raw.size() > NETDISK_MAX) {
                throw new ServiceException("网盘链接最多 " + NETDISK_MAX + " 条，收到 " + raw.size(), 400);
            }
            for (Object o : raw) {
                if (!(o instanceof Map)) {
                    throw new ServiceException("netdisks 元素必须是对象 {provider,url,code?,note?}", 400);
                }
                Map<String, Object> src = (Map<String, Object>) o;
                String provider = str(src.get("provider")).toLowerCase();
                if (!NETDISK_PROVIDERS.contains(provider)) {
                    throw new ServiceException("provider 非法（只能 baidu/quark/aliyun/other）：" + src.get("provider"), 400);
                }
                String url = str(src.get("url"));
                if (url.isEmpty()) {
                    throw new ServiceException("url 不能为空（provider=" + provider + "）", 400);
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("provider", provider);
                m.put("url", url);
                m.put("code", blankToNull(src.get("code")));
                m.put("note", blankToNull(src.get("note")));
                netdisks.add(m);
            }
        }

        mergeStyleMeta(b, Map.of("netdisks", netdisks));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("netdisks", netdisks);
        return r;
    }

    // ───────────────── 宣发文案（style_meta_json.promo，零 DDL） ─────────────────

    /**
     * 保存书的宣发文案（标题 + 描述，覆盖式：传什么就是什么，两者皆空 = 清空）。
     *
     * <p>用途：成品资料发小红书/朋友圈的现成话术，跟着书走不散落——书在哪，文案就在哪。
     * 同 {@link #saveNetdisks} 范式：只覆盖 style_meta_json 的 {@code promo} 键，其余键原样保留。
     *
     * @param bookId 书 id（写门禁 = {@link #requireOwnedBook}）
     * @param raw    {@code {title?, desc?}}；title ≤ 100 字，desc ≤ 3000 字
     * @return {bookId, promo}；promo 为 null 表示已清空
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> savePromo(Long bookId, Map<String, Object> raw) {
        BizShelfBook b = requireOwnedBook(bookId);

        String title = str(raw == null ? null : raw.get("title"));
        String desc = str(raw == null ? null : raw.get("desc"));
        if (title.length() > PROMO_TITLE_MAX) {
            throw new ServiceException("宣发标题最多 " + PROMO_TITLE_MAX + " 字，收到 " + title.length(), 400);
        }
        if (desc.length() > PROMO_DESC_MAX) {
            throw new ServiceException("宣发描述最多 " + PROMO_DESC_MAX + " 字，收到 " + desc.length(), 400);
        }

        Map<String, Object> promo = null;
        if (!title.isEmpty() || !desc.isEmpty()) {
            promo = new LinkedHashMap<>();
            promo.put("title", blankToNull(title));
            promo.put("desc", blankToNull(desc));
        }
        // 🔴 Map.of 不收 null value（清空场景）→ 用 LinkedHashMap 装 patch
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("promo", promo);
        mergeStyleMeta(b, patch);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bookId", String.valueOf(bookId));
        r.put("promo", promo);
        return r;
    }

    /**
     * 🔴 style_meta_json 合并写：读旧 JSON → putAll 增量键 → 只 UPDATE style_meta_json 一列。
     * 绝不整体替换（accent/subtitle/variantName/punchReview 等既有键必须活下来）。
     */
    void mergeStyleMeta(BizShelfBook book, Map<String, Object> patch) {
        Map<String, Object> style = parseMap(book.getStyleMetaJson());
        style.putAll(patch);
        BizShelfBook up = new BizShelfBook();
        up.setId(book.getId());
        up.setStyleMetaJson(JsonUtils.toJsonString(style));
        bookMapper.updateById(up);
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

    // ───────────────── 书章节材料位（book_node_ids 单列，复刻 special_ids 范式） ─────────────────

    /** 课次绑书章节（append，去重）。🔴 只 UPDATE book_node_ids 一列，绝不整行 upsert。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> bindLessonNode(Long lessonId, Long nodeId) {
        BizCoursePlanLesson lesson = requireLessonOwned(lessonId);
        requireBindableNode(nodeId); // node 存在且其书 owner=当前用户或超管
        List<String> ids = parseIdList(lesson.getBookNodeIds());
        String nid = String.valueOf(nodeId);
        if (!ids.contains(nid)) ids.add(nid);
        persistBookNodeIds(lessonId, ids);
        return ids;
    }

    /** 课次解绑书章节（remove）。🔴 只 UPDATE book_node_ids 一列。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> unbindLessonNode(Long lessonId, Long nodeId) {
        BizCoursePlanLesson lesson = requireLessonOwned(lessonId);
        List<String> ids = parseIdList(lesson.getBookNodeIds());
        ids.remove(String.valueOf(nodeId));
        persistBookNodeIds(lessonId, ids);
        return ids;
    }

    /** 查询课次已绑书章节材料（node 反查书：biz_shelf_node.book_id）。 */
    public Map<String, Object> lessonBookMaterials(Long lessonId) {
        BizCoursePlanLesson lesson = requireLessonOwned(lessonId);
        List<String> ids = parseIdList(lesson.getBookNodeIds());
        List<Map<String, Object>> materials = new ArrayList<>();
        for (String id : ids) {
            Long nid = parseId(id);
            if (nid == null) continue;
            BizShelfNode n = nodeMapper.selectById(nid);
            if (n == null) continue; // 节点已删，静默跳过
            BizShelfBook b = bookMapper.selectById(n.getBookId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", String.valueOf(n.getId()));
            m.put("nodeTitle", n.getName());
            m.put("bookId", String.valueOf(n.getBookId()));
            m.put("bookTitle", b == null ? null : b.getTitle());
            // 子树题数（绑「讲」级节点时题在 kp 子节点下，按子树统计才真实）
            List<Long> subtree = collectSubtree(n.getBookId(), n.getId());
            long questionCount = itemMapper.selectCount(new LambdaQueryWrapper<BizShelfItem>()
                .in(BizShelfItem::getNodeId, subtree).eq(BizShelfItem::getKind, "question"));
            m.put("questionCount", questionCount);
            materials.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lessonId", String.valueOf(lessonId));
        r.put("bookNodeIds", ids);
        r.put("materials", materials);
        return r;
    }

    /** 🔴 partial update：仅 book_node_ids（+审计列），不碰 paper_slots/special_ids。 */
    private void persistBookNodeIds(Long lessonId, List<String> ids) {
        BizCoursePlanLesson up = new BizCoursePlanLesson();
        up.setId(lessonId);
        up.setBookNodeIds(JsonUtils.toJsonString(ids));
        lessonMapper.updateById(up);
    }

    /** 课次归属校验：材料位 bind/unbind/查 只能操作本人课次（对齐 SpecialService.requireLesson IDOR 闸）。 */
    private BizCoursePlanLesson requireLessonOwned(Long lessonId) {
        BizCoursePlanLesson l = lessonId == null ? null : lessonMapper.selectById(lessonId);
        if (l == null) throw new ServiceException("课次不存在: lessonId=" + lessonId, 400);
        Long uid = LoginHelper.getUserId();
        if (uid == null) throw new ServiceException("未登录", 401);
        if (l.getCreateBy() != null && !uid.equals(l.getCreateBy()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权操作他人的课次", 403);
        }
        return l;
    }

    /** 绑定校验：node 存在，且其书 owner=当前用户或超管。 */
    private BizShelfNode requireBindableNode(Long nodeId) {
        if (nodeId == null) throw new ServiceException("nodeId 不能为空", 400);
        BizShelfNode n = nodeMapper.selectById(nodeId);
        if (n == null) throw new ServiceException("书章节节点不存在: " + nodeId, 400);
        BizShelfBook b = bookMapper.selectById(n.getBookId());
        if (b == null) throw new ServiceException("章节所属书不存在", 404);
        Long uid = LoginHelper.getUserId();
        boolean pub = b.getIsPublic() != null && b.getIsPublic() == 1;
        if (!pub && uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权绑定他人书籍的章节", 403);
        }
        return n;
    }

    private List<String> parseIdList(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            List<Object> arr = JsonUtils.parseArray(json, Object.class);
            if (arr != null) {
                for (Object o : arr) {
                    if (o == null) continue;
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty() && !out.contains(s)) out.add(s);
                }
            }
        } catch (Exception ignore) {
        }
        return out;
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

    /** 归属校验：书必须存在且属当前登录老师（超管维护旁路，同 requireReadableBook 语义），否则 404/403（水平越权兜底）。 */
    private BizShelfBook requireOwnedBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        Long uid = LoginHelper.getUserId();
        if (uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
            throw new ServiceException("无权访问该书", 403);
        }
        return b;
    }

    /** 可读校验：owner 本人 / is_public=1 / 超管 三者其一即可读（写路径仍走 requireOwnedBook）。 */
    private BizShelfBook requireReadableBook(Long bookId) {
        BizShelfBook b = bookMapper.selectById(bookId);
        if (b == null) throw new ServiceException("书不存在", 404);
        if (b.getIsPublic() != null && b.getIsPublic() == 1) return b;
        Long uid = LoginHelper.getUserId();
        if (uid != null && b.getOwnerId() != null && !uid.equals(b.getOwnerId()) && !LoginHelper.isSuperAdmin()) {
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
        m.put("isPublic", b.getIsPublic() != null && b.getIsPublic() == 1);
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

    /**
     * JSON 对象列解析成可改的 Map。空列 → 空 Map；
     * 🔴 非空但解析不出对象 → 直接抛，绝不静默当空 Map 继续写（那会把整列既有键抹掉）。
     */
    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        Map<String, Object> m;
        try {
            m = JsonUtils.parseMap(json);
        } catch (Exception e) {
            throw new ServiceException("style_meta_json 解析失败，拒绝覆盖写：" + e.getMessage(), 500);
        }
        if (m == null) {
            throw new ServiceException("style_meta_json 不是 JSON 对象，拒绝覆盖写", 500);
        }
        return new LinkedHashMap<>(m);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private String blankToNull(Object o) {
        String s = str(o);
        return s.isEmpty() ? null : s;
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
