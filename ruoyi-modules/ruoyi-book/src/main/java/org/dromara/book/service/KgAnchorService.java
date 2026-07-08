package org.dromara.book.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.BizSubject;
import org.dromara.book.mapper.BizSubjectMapper;
import org.dromara.book.util.SplitClient;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * KG 锚定服务（PRD-A-024 批2 scope⑤ / 决策 D10 三段式）。
 *
 * <p>把打标产出的主考点名（dna_json.main_kp.name）锚到 biz_subject 学科子树的真实节点，治「孤儿题挂不上章节树」。
 * 三段式，确定性优先，LLM 只在多候选时单选，绝不让 LLM 自由发挥节点名：
 * <ol>
 *   <li><b>确定性</b>：主考点名在 job.subjectId 子树叶子里名字匹配（精确 &gt; 归一化相等 &gt; 包含），唯一命中→直接锚。</li>
 *   <li><b>多候选</b>：候选叶子给 toolkit /kg_pick（LLM 走 _ainvoke_text，落 conv_trace）单选一个 id。</li>
 *   <li><b>兜底</b>：无合适→挂学科根（level1）综合节点，fallback=true（非 NULL 即可检索，占比如实上报）。</li>
 * </ol>
 *
 * <p>子树至多几百节点，worker 按 job 调 {@link #loadSubtree} 一次、逐题复用。
 *
 * @author backend-dev (PRD-A-024 批2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KgAnchorService {

    private final BizSubjectMapper subjectMapper;
    private final SplitClient splitClient;

    /** 锚定结果（供 worker 序列化进 kp_anchor_json + commit 读回写 biz_question_knowledge）。 */
    public static class Anchor {
        /** 锚到的 biz_subject.id（fallback 时=学科根 id；始终非空真实节点） */
        public String kpId;
        /** 主考点名（打标产出，原样留档供审核对照） */
        public String kpName;
        /** 实际匹配到的 biz_subject 节点名 */
        public String matchedName;
        /** 命中段：exact/norm/contains/llm/dna_id/fallback */
        public String stage;
        /** 置信度 0-1（审核页展示；biz_question_knowledge 无 confidence 列，仅存于 item.kp_anchor_json） */
        public double confidence;
        /** 是否兜底（挂了学科综合节点，未命中具体考点） */
        public boolean fallback;
    }

    /** 载入学科根子树（含根）；worker 按 job 调一次，逐题复用免重复查。 */
    public List<BizSubject> loadSubtree(String subjectId) {
        if (StringUtils.isBlank(subjectId)) {
            return List.of();
        }
        try {
            List<BizSubject> nodes = subjectMapper.selectSubtree(subjectId);
            return nodes == null ? List.of() : nodes;
        } catch (Exception e) {
            log.warn("[kg-anchor] 载入子树失败 subjectId={} err={}", subjectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 三段式锚定一题。
     *
     * @param subjectId 学科根 id（job.subjectId，兜底节点）
     * @param subtree   {@link #loadSubtree} 结果（复用）
     * @param dnaKpId   dna 主考点 id（打标一般为空；非空且在子树内→直接用，stage=dna_id）
     * @param kpName    dna 主考点名
     * @param stem      题干（多候选时喂 LLM 单选）
     * @param teacherId 归因透传
     * @return Anchor；无法确定具体考点则 fallback 挂学科根。kpName 空或子树空 → 返回 null（不锚）。
     */
    public Anchor resolve(String subjectId, List<BizSubject> subtree,
                          String dnaKpId, String kpName, String stem, Long teacherId) {
        if (subtree == null || subtree.isEmpty() || StringUtils.isBlank(kpName)) {
            return null;
        }
        // 建 id→node、parent 链、叶子集
        Map<String, BizSubject> byId = new HashMap<>();
        Map<String, Integer> childCount = new HashMap<>();
        for (BizSubject n : subtree) {
            byId.put(n.getId(), n);
        }
        for (BizSubject n : subtree) {
            String pid = n.getParentId();
            if (pid != null && byId.containsKey(pid)) {
                childCount.merge(pid, 1, Integer::sum);
            }
        }
        List<BizSubject> leaves = new ArrayList<>();
        for (BizSubject n : subtree) {
            if (childCount.getOrDefault(n.getId(), 0) == 0) {
                leaves.add(n);
            }
        }

        // 0) dna 已带 id 且在子树内 → 直接锚（打标一般 id 为空，留此路兜将来）
        if (StringUtils.isNotBlank(dnaKpId) && byId.containsKey(dnaKpId)) {
            return build(dnaKpId, kpName, byId.get(dnaKpId).getName(), "dna_id", 1.0, false);
        }

        String nn = norm(kpName);
        // 1a) 精确
        List<BizSubject> exact = new ArrayList<>();
        for (BizSubject l : leaves) {
            if (kpName.equals(l.getName())) {
                exact.add(l);
            }
        }
        if (exact.size() == 1) {
            return build(exact.get(0).getId(), kpName, exact.get(0).getName(), "exact", 1.0, false);
        }
        // 1b) 归一化相等
        List<BizSubject> normEq = new ArrayList<>();
        if (exact.isEmpty()) {
            for (BizSubject l : leaves) {
                if (!nn.isEmpty() && nn.equals(norm(l.getName()))) {
                    normEq.add(l);
                }
            }
            if (normEq.size() == 1) {
                return build(normEq.get(0).getId(), kpName, normEq.get(0).getName(), "norm", 0.95, false);
            }
        }
        // 1c) 包含（双向，严格 norm）
        List<BizSubject> contains = new ArrayList<>();
        if (exact.isEmpty() && normEq.isEmpty()) {
            for (BizSubject l : leaves) {
                String ln = norm(l.getName());
                if (!nn.isEmpty() && !ln.isEmpty() && (ln.contains(nn) || nn.contains(ln))) {
                    contains.add(l);
                }
            }
            if (contains.size() == 1) {
                return build(contains.get(0).getId(), kpName, contains.get(0).getName(), "contains", 0.85, false);
            }
        }
        // 1d) 宽松包含（再去虚词 的/和/与/地/得/了）：捕「显微镜的结构和使用↔与使用」「长度测量的规范操作↔长度的测量」类近义一字差。
        List<BizSubject> loose = new ArrayList<>();
        if (exact.isEmpty() && normEq.isEmpty() && contains.isEmpty()) {
            String ns = normLoose(kpName);
            for (BizSubject l : leaves) {
                String ls = normLoose(l.getName());
                if (!ns.isEmpty() && !ls.isEmpty() && (ls.contains(ns) || ns.contains(ls))) {
                    loose.add(l);
                }
            }
            if (loose.size() == 1) {
                return build(loose.get(0).getId(), kpName, loose.get(0).getName(), "loose", 0.75, false);
            }
        }

        // 2) 多候选 → LLM 单选（按优先级取第一非空候选段，去重）
        List<BizSubject> cands = !exact.isEmpty() ? exact
            : (!normEq.isEmpty() ? normEq : (!contains.isEmpty() ? contains : loose));
        if (cands.size() > 1) {
            try {
                List<Map<String, Object>> candPayload = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (BizSubject l : cands) {
                    if (!seen.add(l.getId())) {
                        continue;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", l.getId());
                    m.put("path", pathOf(l.getId(), byId));
                    candPayload.add(m);
                }
                String chosenId = splitClient.kgPick(stem, kpName, candPayload, teacherId);
                if (StringUtils.isNotBlank(chosenId) && byId.containsKey(chosenId)) {
                    return build(chosenId, kpName, byId.get(chosenId).getName(), "llm", 0.7, false);
                }
            } catch (Exception e) {
                log.warn("[kg-anchor] LLM 单选失败 kpName={} err={}（转兜底）", kpName, e.getMessage());
            }
        }

        // 3) 兜底：挂学科根（综合节点），非 NULL 即可检索
        BizSubject root = byId.get(subjectId);
        String rootId = root != null ? root.getId() : subjectId;
        String rootName = root != null ? root.getName() : subjectId;
        return build(rootId, kpName, rootName, "fallback", 0.3, true);
    }

    private static Anchor build(String kpId, String kpName, String matchedName,
                                String stage, double conf, boolean fallback) {
        Anchor a = new Anchor();
        a.kpId = kpId;
        a.kpName = kpName;
        a.matchedName = matchedName;
        a.stage = stage;
        a.confidence = conf;
        a.fallback = fallback;
        return a;
    }

    /** 节点 → 「根 &gt; … &gt; 叶子」路径（供 LLM 判位）。 */
    private static String pathOf(String id, Map<String, BizSubject> byId) {
        List<String> names = new ArrayList<>();
        String cur = id;
        int guard = 0;
        while (cur != null && byId.containsKey(cur) && guard++ < 20) {
            BizSubject n = byId.get(cur);
            names.add(0, n.getName());
            cur = n.getParentId();
        }
        return String.join(" > ", names);
    }

    /** 归一化：去空白 + 常见中英标点，用于容错名字匹配。 */
    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\u3000（）()《》【】\\[\\]{}\"'“”‘’·、，,。.：:；;！!？?\\-—_~～]", "").trim();
    }

    /** 宽松归一化：在 {@link #norm} 基础上再去结构虚词（的/和/与/地/得/了），捕近义一字差。仅第 1d 段用。 */
    static String normLoose(String s) {
        return norm(s).replaceAll("[的和与地得了及以]", "");
    }
}
