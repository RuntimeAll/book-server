package org.dromara.book.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.SpecialBook;
import org.dromara.book.domain.entity.SpecialItem;
import org.dromara.book.domain.entity.SpecialNode;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.SpecialBookMapper;
import org.dromara.book.mapper.SpecialItemMapper;
import org.dromara.book.mapper.SpecialNodeMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 专项（book_type='special'）编排领域服务（PRD-003 C 位，跨线契约 §4「Special 编排端点」）。
 *
 * <p>直读写 biz_shelf_* 三表（A 位 Shelf 实体层未合 master 时的自建薄层，集成段收敛）。
 * 覆盖：专项 CRUD、区块 sec/难度档 tier 节点增删改序、题目增删改（override 副本）/拖排/留空高度、
 * 冻结面 {@code pick}（questionId 单入 / nodeId 整节批量入）、课次材料位（special_ids 单列）。
 *
 * <p>归属纪律：所有写操作先 {@link #requireOwnedBook} 校验专项归属当前登录用户，杜绝水平越权。
 * 留空高度纪律：biz_shelf_item 无 meta 列，题目 gap 以保留键 {@code __gap} 内嵌 override_json，
 * 回写题库前按 {@code __} 前缀剥离（本卡不回写，仅导出读取）。
 *
 * @author codeplace-C PRD-003
 */
@Service
@RequiredArgsConstructor
public class SpecialService {

    private final SpecialBookMapper bookMapper;
    private final SpecialNodeMapper nodeMapper;
    private final SpecialItemMapper itemMapper;
    private final BizQuestionMapper questionMapper;
    private final BizCoursePlanLessonMapper lessonMapper;

    private static final String BOOK_TYPE = "special";
    private static final String KIND_QUESTION = "question";
    private static final String NODE_SEC = "sec";
    private static final String GAP_KEY = "__gap";
    private static final String DEFAULT_SEC = "综合";

    // ───────────────── 专项书 CRUD ─────────────────

    /** 创建专项书（book_type='special'，owner=当前登录用户）。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createSpecial(Map<String, Object> body) {
        Long uid = requireLogin();
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) {
            throw new ServiceException("专项标题必填", 400);
        }
        SpecialBook b = new SpecialBook();
        b.setId(IdUtil.getSnowflakeNextId());
        b.setBookType(BOOK_TYPE);
        b.setTitle(title.trim());
        b.setSubjectId(str(body.get("subjectId")));
        b.setGrade(str(body.get("grade")));
        b.setEdition(str(body.get("edition")));
        b.setOwnerId(uid);
        b.setStatus("0");
        Object style = body.get("styleMetaJson");
        if (style != null) {
            b.setStyleMetaJson(style instanceof String s ? s : JsonUtils.toJsonString(style));
        }
        b.setRemark(str(body.get("remark")));
        bookMapper.insert(b);
        return b.getId();
    }

    /** 我的专项列表（备课栏用）。 */
    public List<Map<String, Object>> listMine() {
        Long uid = requireLogin();
        LambdaQueryWrapper<SpecialBook> qw = new LambdaQueryWrapper<SpecialBook>()
            .eq(SpecialBook::getBookType, BOOK_TYPE)
            .eq(SpecialBook::getOwnerId, uid)
            .orderByDesc(SpecialBook::getId);
        List<SpecialBook> books = bookMapper.selectList(qw);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpecialBook b : books) {
            Map<String, Object> m = bookSummary(b);
            m.put("itemCount", countItems(b.getId()));
            out.add(m);
        }
        return out;
    }

    /** 专项全树详情：book + secs[{node, tiers[{node, items}], items}]。 */
    public Map<String, Object> detail(Long specialId) {
        SpecialBook book = requireOwnedBook(specialId);
        List<SpecialNode> nodes = nodeMapper.selectList(
            new LambdaQueryWrapper<SpecialNode>().eq(SpecialNode::getBookId, specialId)
                .orderByAsc(SpecialNode::getSeq).orderByAsc(SpecialNode::getId));
        List<SpecialItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<SpecialItem>().eq(SpecialItem::getBookId, specialId)
                .orderByAsc(SpecialItem::getSeq).orderByAsc(SpecialItem::getId));
        // node_id -> items
        Map<Long, List<SpecialItem>> itemsByNode = new LinkedHashMap<>();
        for (SpecialItem it : items) {
            itemsByNode.computeIfAbsent(it.getNodeId(), k -> new ArrayList<>()).add(it);
        }
        // parent_id -> child nodes
        Map<Long, List<SpecialNode>> childrenOf = new LinkedHashMap<>();
        List<SpecialNode> roots = new ArrayList<>();
        for (SpecialNode n : nodes) {
            if (n.getParentId() == null) {
                roots.add(n);
            } else {
                childrenOf.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
            }
        }
        List<Map<String, Object>> secVos = new ArrayList<>();
        for (SpecialNode sec : roots) {
            Map<String, Object> secVo = nodeVo(sec);
            // tier 子节点
            List<Map<String, Object>> tierVos = new ArrayList<>();
            for (SpecialNode tier : childrenOf.getOrDefault(sec.getId(), List.of())) {
                Map<String, Object> tierVo = nodeVo(tier);
                tierVo.put("items", itemVos(itemsByNode.get(tier.getId())));
                tierVos.add(tierVo);
            }
            secVo.put("tiers", tierVos);
            // 直挂 sec 的题（无 tier 分组）
            secVo.put("items", itemVos(itemsByNode.get(sec.getId())));
            secVos.add(secVo);
        }
        Map<String, Object> r = bookSummary(book);
        r.put("secs", secVos);
        return r;
    }

    /** 更新专项书元信息（title/subjectId/grade/edition/styleMetaJson/status）。仅改传入列。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateBook(Long specialId, Map<String, Object> body) {
        requireOwnedBook(specialId);
        SpecialBook up = new SpecialBook();
        up.setId(specialId);
        if (body.containsKey("title")) {
            String t = str(body.get("title"));
            if (t == null || t.isBlank()) throw new ServiceException("专项标题不能置空", 400);
            up.setTitle(t.trim());
        }
        if (body.containsKey("subjectId")) up.setSubjectId(str(body.get("subjectId")));
        if (body.containsKey("grade")) up.setGrade(str(body.get("grade")));
        if (body.containsKey("edition")) up.setEdition(str(body.get("edition")));
        if (body.containsKey("status")) up.setStatus(str(body.get("status")));
        if (body.containsKey("styleMetaJson")) {
            Object style = body.get("styleMetaJson");
            up.setStyleMetaJson(style == null ? null : (style instanceof String s ? s : JsonUtils.toJsonString(style)));
        }
        bookMapper.updateById(up);
    }

    /** 删除专项书（级联删节点 + 内容项）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpecial(Long specialId) {
        requireOwnedBook(specialId);
        itemMapper.delete(new LambdaQueryWrapper<SpecialItem>().eq(SpecialItem::getBookId, specialId));
        nodeMapper.delete(new LambdaQueryWrapper<SpecialNode>().eq(SpecialNode::getBookId, specialId));
        bookMapper.deleteById(specialId);
    }

    // ───────────────── 节点（sec/tier）增删改序 ─────────────────

    /** 新建节点（sec 区块 / tier 难度档）。parentId 为空=根层 sec；非空=挂在该 sec 下的 tier。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createNode(Long specialId, Map<String, Object> body) {
        SpecialBook book = requireOwnedBook(specialId);
        String name = str(body.get("name"));
        if (name == null || name.isBlank()) throw new ServiceException("节点名必填（卷面可见，禁内部词）", 400);
        Long parentId = lng(body.get("parentId"));
        if (parentId != null) {
            SpecialNode parent = requireNodeInBook(parentId, specialId);
            if (parent.getParentId() != null) {
                throw new ServiceException("节点树仅两层（sec→tier），不能在 tier 下再建节点", 400);
            }
        }
        SpecialNode n = new SpecialNode();
        n.setId(IdUtil.getSnowflakeNextId());
        n.setBookId(book.getId());
        n.setParentId(parentId);
        n.setNodeType(str(body.get("nodeType")) != null ? str(body.get("nodeType"))
            : (parentId == null ? NODE_SEC : "tier"));
        n.setName(name.trim());
        n.setKpId(lng(body.get("kpId")));
        Object meta = body.get("meta") != null ? body.get("meta") : body.get("metaJson");
        if (meta != null) n.setMetaJson(meta instanceof String s ? s : JsonUtils.toJsonString(meta));
        Integer seq = intg(body.get("seq"));
        n.setSeq(seq != null ? seq : nextNodeSeq(specialId, parentId));
        nodeMapper.insert(n);
        return n.getId();
    }

    /** 改节点（name/nodeType/meta/seq）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(Long nodeId, Map<String, Object> body) {
        SpecialNode node = requireOwnedNode(nodeId);
        SpecialNode up = new SpecialNode();
        up.setId(nodeId);
        if (body.containsKey("name")) {
            String nm = str(body.get("name"));
            if (nm == null || nm.isBlank()) throw new ServiceException("节点名不能置空", 400);
            up.setName(nm.trim());
        }
        if (body.containsKey("nodeType")) up.setNodeType(str(body.get("nodeType")));
        if (body.containsKey("seq")) up.setSeq(intg(body.get("seq")));
        if (body.containsKey("kpId")) up.setKpId(lng(body.get("kpId")));
        if (body.containsKey("meta") || body.containsKey("metaJson")) {
            Object meta = body.containsKey("meta") ? body.get("meta") : body.get("metaJson");
            up.setMetaJson(meta == null ? null : (meta instanceof String s ? s : JsonUtils.toJsonString(meta)));
        }
        nodeMapper.updateById(up);
        // 触发 update_time；node 引用未变
    }

    /** 删节点（级联删子节点 tier + 其下所有 item）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        SpecialNode node = requireOwnedNode(nodeId);
        List<Long> nodeIds = new ArrayList<>();
        nodeIds.add(nodeId);
        // 收集子节点（两层树：本节点若为 sec，子 tier 一并删）
        List<SpecialNode> children = nodeMapper.selectList(
            new LambdaQueryWrapper<SpecialNode>().eq(SpecialNode::getParentId, nodeId));
        for (SpecialNode c : children) nodeIds.add(c.getId());
        itemMapper.delete(new LambdaQueryWrapper<SpecialItem>().in(SpecialItem::getNodeId, nodeIds));
        nodeMapper.delete(new LambdaQueryWrapper<SpecialNode>().in(SpecialNode::getId, nodeIds));
    }

    /** 同层节点重排（parentId 下按 nodeIds 顺序写 seq）。 */
    @Transactional(rollbackFor = Exception.class)
    public void reorderNodes(Long specialId, List<Long> nodeIds) {
        requireOwnedBook(specialId);
        if (nodeIds == null) return;
        int seq = 0;
        for (Long nid : nodeIds) {
            SpecialNode node = requireNodeInBook(nid, specialId);
            SpecialNode up = new SpecialNode();
            up.setId(node.getId());
            up.setSeq(seq++);
            nodeMapper.updateById(up);
        }
    }

    // ───────────────── 题目（item）增删改 / 拖排 / 留空高度 ─────────────────

    /** 向节点添加一题（引用 questionId + override 副本 + 留空高度 gap）。 */
    @Transactional(rollbackFor = Exception.class)
    public Long addItem(Long nodeId, Map<String, Object> body) {
        SpecialNode node = requireOwnedNode(nodeId);
        Long questionId = lng(body.get("questionId"));
        if (questionId == null) throw new ServiceException("questionId 必填", 400);
        if (questionMapper.selectById(questionId) == null) {
            throw new ServiceException("题目不存在: questionId=" + questionId, 400);
        }
        SpecialItem it = buildQuestionItem(node.getBookId(), nodeId, questionId,
            body.get("overrideJson"), intg(body.get("gap")),
            nextItemSeq(nodeId));
        itemMapper.insert(it);
        return it.getId();
    }

    /** 改题（override 副本 / 留空高度 gap / 移动到 nodeId / seq）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long itemId, Map<String, Object> body) {
        SpecialItem item = requireOwnedItem(itemId);
        SpecialItem up = new SpecialItem();
        up.setId(itemId);
        boolean touchOverride = false;
        Map<String, Object> override = parseObjMap(item.getOverrideJson());
        if (body.containsKey("overrideJson")) {
            Object ov = body.get("overrideJson");
            Map<String, Object> incoming = ov == null ? new LinkedHashMap<>()
                : (ov instanceof String s ? parseObjMap(s) : toMap(ov));
            Object keepGap = override.get(GAP_KEY); // 保留原 gap，除非本次也传 gap
            override = incoming;
            if (keepGap != null && !body.containsKey("gap")) override.put(GAP_KEY, keepGap);
            touchOverride = true;
        }
        if (body.containsKey("gap")) {
            Integer gap = intg(body.get("gap"));
            if (gap == null) override.remove(GAP_KEY); else override.put(GAP_KEY, gap);
            touchOverride = true;
        }
        if (touchOverride) {
            up.setOverrideJson(override.isEmpty() ? null : JsonUtils.toJsonString(override));
        }
        if (body.containsKey("nodeId")) {
            Long target = lng(body.get("nodeId"));
            if (target != null) {
                SpecialNode tn = requireNodeInBook(target, item.getBookId());
                up.setNodeId(tn.getId());
                if (!body.containsKey("seq")) up.setSeq(nextItemSeq(target));
            }
        }
        if (body.containsKey("seq")) up.setSeq(intg(body.get("seq")));
        // 若 override 被清空为 null，updateById 的 NOT_NULL 策略不会写 null——用 UpdateWrapper 处理清空
        if (touchOverride && up.getOverrideJson() == null) {
            itemMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SpecialItem>()
                .eq(SpecialItem::getId, itemId).set(SpecialItem::getOverrideJson, null));
            up.setOverrideJson(null); // 避免 updateById 再处理
        }
        // 其余列走 updateById（仅非空列）
        if (up.getNodeId() != null || up.getSeq() != null || up.getOverrideJson() != null) {
            itemMapper.updateById(up);
        }
    }

    /** 删题。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long itemId) {
        requireOwnedItem(itemId);
        itemMapper.deleteById(itemId);
    }

    /** 节点内题目重排（拖排：按 itemIds 顺序写 seq）。 */
    @Transactional(rollbackFor = Exception.class)
    public void reorderItems(Long nodeId, List<Long> itemIds) {
        SpecialNode node = requireOwnedNode(nodeId);
        if (itemIds == null) return;
        int seq = 0;
        for (Long iid : itemIds) {
            SpecialItem it = itemMapper.selectById(iid);
            if (it == null || !node.getId().equals(it.getNodeId())) {
                throw new ServiceException("题目不属于该节点: itemId=" + iid, 400);
            }
            SpecialItem up = new SpecialItem();
            up.setId(iid);
            up.setSeq(seq++);
            itemMapper.updateById(up);
        }
    }

    // ───────────────── 冻结面 pick（契约 §3） ─────────────────

    /**
     * 挑题入专项（跨线契约冻结面）。body {questionId?|nodeId?, overrideJson?, secHint?}。
     * <ul>
     *   <li>questionId：单题入；</li>
     *   <li>nodeId：源书节点整节题目批量入（读 biz_shelf_item where node_id=nodeId and kind=question）。</li>
     * </ul>
     * secHint：目标区块名（找不到则在本专项新建同名 sec）；缺省落默认区块「综合」。
     *
     * @return {secId, addedItemIds:[...]}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pick(Long specialId, Map<String, Object> body) {
        SpecialBook book = requireOwnedBook(specialId);
        Long questionId = lng(body.get("questionId"));
        Long nodeId = lng(body.get("nodeId"));
        if (questionId == null && nodeId == null) {
            throw new ServiceException("questionId 与 nodeId 至少传一个", 400);
        }
        Object overrideJson = body.get("overrideJson");
        String secHint = str(body.get("secHint"));
        Long secId = resolveSec(book.getId(), secHint);
        List<String> added = new ArrayList<>();
        int seq = nextItemSeq(secId);
        if (questionId != null) {
            if (questionMapper.selectById(questionId) == null) {
                throw new ServiceException("题目不存在: questionId=" + questionId, 400);
            }
            SpecialItem it = buildQuestionItem(book.getId(), secId, questionId, overrideJson, null, seq++);
            itemMapper.insert(it);
            added.add(String.valueOf(it.getId()));
        }
        if (nodeId != null) {
            // 源书节点整节题目批量入（同表 biz_shelf_item 读源节点 question 项）
            List<SpecialItem> src = itemMapper.selectList(
                new LambdaQueryWrapper<SpecialItem>()
                    .eq(SpecialItem::getNodeId, nodeId)
                    .eq(SpecialItem::getKind, KIND_QUESTION)
                    .orderByAsc(SpecialItem::getSeq).orderByAsc(SpecialItem::getId));
            for (SpecialItem s : src) {
                if (s.getQuestionId() == null) continue;
                // 副本复制：源 override_json 保留（若 pick 带 overrideJson 则覆盖统一副本）
                Object ov = overrideJson != null ? overrideJson : s.getOverrideJson();
                SpecialItem it = buildQuestionItem(book.getId(), secId, s.getQuestionId(), ov, null, seq++);
                itemMapper.insert(it);
                added.add(String.valueOf(it.getId()));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("secId", String.valueOf(secId));
        r.put("addedItemIds", added);
        r.put("addedCount", added.size());
        return r;
    }

    // ───────────────── 材料位（special_ids 单列） ─────────────────

    /** 课次绑专项（append，去重）。🔴 只 UPDATE special_ids 一列。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> bindLesson(Long lessonId, Long specialId) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        requireOwnedBook(specialId); // 只能绑本人的专项
        List<String> ids = parseIdList(lesson.getSpecialIds());
        String sid = String.valueOf(specialId);
        if (!ids.contains(sid)) ids.add(sid);
        persistSpecialIds(lessonId, ids);
        return ids;
    }

    /** 课次解绑专项（remove）。🔴 只 UPDATE special_ids 一列。 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> unbindLesson(Long lessonId, Long specialId) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        List<String> ids = parseIdList(lesson.getSpecialIds());
        ids.remove(String.valueOf(specialId));
        persistSpecialIds(lessonId, ids);
        return ids;
    }

    /** 查询课次材料（已绑专项书概要列表）。 */
    public Map<String, Object> lessonMaterials(Long lessonId) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        List<String> ids = parseIdList(lesson.getSpecialIds());
        List<Map<String, Object>> specials = new ArrayList<>();
        for (String id : ids) {
            Long sid = lng(id);
            if (sid == null) continue;
            SpecialBook b = bookMapper.selectById(sid);
            if (b == null) continue; // 专项已删，静默跳过
            Map<String, Object> m = bookSummary(b);
            m.put("itemCount", countItems(b.getId()));
            specials.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("lessonId", String.valueOf(lessonId));
        r.put("specialIds", ids);
        r.put("specials", specials);
        return r;
    }

    // ───────────────── 内部工具 ─────────────────

    private SpecialItem buildQuestionItem(Long bookId, Long nodeId, Long questionId,
                                          Object overrideJson, Integer gap, int seq) {
        SpecialItem it = new SpecialItem();
        it.setId(IdUtil.getSnowflakeNextId());
        it.setBookId(bookId);
        it.setNodeId(nodeId);
        it.setSeq(seq);
        it.setKind(KIND_QUESTION);
        it.setQuestionId(questionId);
        it.setUsedCount(0);
        Map<String, Object> override = overrideJson == null ? new LinkedHashMap<>()
            : (overrideJson instanceof String s ? parseObjMap(s) : toMap(overrideJson));
        if (gap != null) override.put(GAP_KEY, gap);
        it.setOverrideJson(override.isEmpty() ? null : JsonUtils.toJsonString(override));
        return it;
    }

    /** 找/建目标区块 sec。secHint 匹配同名根 sec，否则新建。缺省用「综合」。 */
    private Long resolveSec(Long bookId, String secHint) {
        String name = (secHint == null || secHint.isBlank()) ? DEFAULT_SEC : secHint.trim();
        List<SpecialNode> secs = nodeMapper.selectList(
            new LambdaQueryWrapper<SpecialNode>()
                .eq(SpecialNode::getBookId, bookId)
                .isNull(SpecialNode::getParentId));
        for (SpecialNode s : secs) {
            if (name.equals(s.getName())) return s.getId();
        }
        SpecialNode sec = new SpecialNode();
        sec.setId(IdUtil.getSnowflakeNextId());
        sec.setBookId(bookId);
        sec.setParentId(null);
        sec.setNodeType(NODE_SEC);
        sec.setName(name);
        sec.setSeq(nextNodeSeq(bookId, null));
        nodeMapper.insert(sec);
        return sec.getId();
    }

    private int nextNodeSeq(Long bookId, Long parentId) {
        LambdaQueryWrapper<SpecialNode> qw = new LambdaQueryWrapper<SpecialNode>()
            .eq(SpecialNode::getBookId, bookId);
        if (parentId == null) qw.isNull(SpecialNode::getParentId);
        else qw.eq(SpecialNode::getParentId, parentId);
        List<SpecialNode> sib = nodeMapper.selectList(qw);
        int max = -1;
        for (SpecialNode n : sib) if (n.getSeq() != null && n.getSeq() > max) max = n.getSeq();
        return max + 1;
    }

    private int nextItemSeq(Long nodeId) {
        List<SpecialItem> items = itemMapper.selectList(
            new LambdaQueryWrapper<SpecialItem>().eq(SpecialItem::getNodeId, nodeId));
        int max = -1;
        for (SpecialItem it : items) if (it.getSeq() != null && it.getSeq() > max) max = it.getSeq();
        return max + 1;
    }

    private long countItems(Long bookId) {
        return itemMapper.selectCount(
            new LambdaQueryWrapper<SpecialItem>().eq(SpecialItem::getBookId, bookId));
    }

    private void persistSpecialIds(Long lessonId, List<String> ids) {
        BizCoursePlanLesson up = new BizCoursePlanLesson();
        up.setId(lessonId);
        up.setSpecialIds(JsonUtils.toJsonString(ids));
        lessonMapper.updateById(up); // partial：仅 special_ids（+审计列），不碰 paper_slots
    }

    // ── 归属校验 ──

    private Long requireLogin() {
        Long uid = LoginHelper.getUserId();
        if (uid == null) throw new ServiceException("未登录", 401);
        return uid;
    }

    private SpecialBook requireOwnedBook(Long specialId) {
        if (specialId == null) throw new ServiceException("specialId 不能为空", 400);
        SpecialBook b = bookMapper.selectById(specialId);
        if (b == null || !BOOK_TYPE.equals(b.getBookType())) {
            throw new ServiceException("专项不存在: " + specialId, 400);
        }
        Long uid = requireLogin();
        if (b.getOwnerId() != null && !uid.equals(b.getOwnerId())) {
            throw new ServiceException("无权操作他人的专项", 403);
        }
        return b;
    }

    private SpecialNode requireOwnedNode(Long nodeId) {
        if (nodeId == null) throw new ServiceException("nodeId 不能为空", 400);
        SpecialNode n = nodeMapper.selectById(nodeId);
        if (n == null) throw new ServiceException("节点不存在: " + nodeId, 400);
        requireOwnedBook(n.getBookId());
        return n;
    }

    private SpecialNode requireNodeInBook(Long nodeId, Long bookId) {
        SpecialNode n = nodeMapper.selectById(nodeId);
        if (n == null || !bookId.equals(n.getBookId())) {
            throw new ServiceException("节点不属于该专项: " + nodeId, 400);
        }
        return n;
    }

    private SpecialItem requireOwnedItem(Long itemId) {
        if (itemId == null) throw new ServiceException("itemId 不能为空", 400);
        SpecialItem it = itemMapper.selectById(itemId);
        if (it == null) throw new ServiceException("题目项不存在: " + itemId, 400);
        requireOwnedBook(it.getBookId());
        return it;
    }

    private BizCoursePlanLesson requireLesson(Long lessonId) {
        BizCoursePlanLesson l = lessonId == null ? null : lessonMapper.selectById(lessonId);
        if (l == null) throw new ServiceException("课次不存在: lessonId=" + lessonId, 400);
        return l;
    }

    // ── VO 组装 ──

    private Map<String, Object> bookSummary(SpecialBook b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(b.getId()));
        m.put("bookType", b.getBookType());
        m.put("title", b.getTitle());
        m.put("subjectId", b.getSubjectId());
        m.put("grade", b.getGrade());
        m.put("status", b.getStatus());
        m.put("styleMetaJson", b.getStyleMetaJson());
        return m;
    }

    private Map<String, Object> nodeVo(SpecialNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(n.getId()));
        m.put("parentId", n.getParentId() == null ? null : String.valueOf(n.getParentId()));
        m.put("nodeType", n.getNodeType());
        m.put("name", n.getName());
        m.put("seq", n.getSeq());
        m.put("kpId", n.getKpId() == null ? null : String.valueOf(n.getKpId()));
        m.put("meta", n.getMetaJson() == null ? null : parseObjMap(n.getMetaJson()));
        return m;
    }

    private List<Map<String, Object>> itemVos(List<SpecialItem> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (SpecialItem it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(it.getId()));
            m.put("kind", it.getKind());
            m.put("questionId", it.getQuestionId() == null ? null : String.valueOf(it.getQuestionId()));
            m.put("seq", it.getSeq());
            m.put("usedCount", it.getUsedCount());
            Map<String, Object> override = parseObjMap(it.getOverrideJson());
            Object gap = override.remove(GAP_KEY);
            m.put("gap", gap);
            m.put("override", override.isEmpty() ? null : override);
            out.add(m);
        }
        return out;
    }

    // ── 基础转换 ──

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s;
    }

    private Long lng(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            throw new ServiceException("非法数字: " + o, 400);
        }
    }

    private Integer intg(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            throw new ServiceException("非法整数: " + o, 400);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object o) {
        if (o instanceof Map<?, ?> m) return new LinkedHashMap<>((Map<String, Object>) m);
        return new LinkedHashMap<>();
    }

    private Map<String, Object> parseObjMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> m = JsonUtils.parseMap(json);
            return m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
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
}
