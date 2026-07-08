package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizPaper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizPaperMapper;
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
 * 专项卷位（paper_slots）领域服务（PRD-B-101 B1）。
 *
 * <p>集中承载 lesson.paper_slots 的 解析 / 备课态推导 / 绑卷位 / 解绑 / 标记已备好 / 写入校验，
 * 供 {@link CoursePlanService}（upsert/展示）与 {@code PaperLibraryServiceImpl}（创建即绑）复用，
 * 避免 JSON 操作与推导规则散落多处漂移。
 *
 * <p>备课态推导（服务端唯一权威，替代旧 biz_prep_pack.status）：
 * <ol>
 *   <li>任一卷位 manual_ready=true → 已备好 '2'（手动覆盖，任意时刻优先）</li>
 *   <li>否则 卷位数&gt;0 且 全部 paper_id 非空 → 已备好 '2'</li>
 *   <li>否则 任一 paper_id 非空 → 备课中 '1'</li>
 *   <li>否则 → 未备 '0'</li>
 * </ol>
 * 🔴 任一卷位解绑时 manual_ready 自动清 false（G5 反性）。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PaperSlotService {

    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizPaperMapper paperMapper;

    /** 解析 paper_slots JSON 为可变 List（null/空 → 空 List）。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseSlots(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<Object> arr = JsonUtils.parseArray(json, Object.class);
            List<Map<String, Object>> out = new ArrayList<>();
            if (arr != null) {
                for (Object o : arr) {
                    if (o instanceof Map<?, ?> m) out.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 备课态推导（唯一权威）。入参 = lesson.paper_slots JSON 字符串。 */
    public String deriveStatus(String paperSlotsJson) {
        return deriveStatusFromList(parseSlots(paperSlotsJson));
    }

    /** 备课态推导（唯一权威）。入参 = 已解析卷位列表。 */
    public String deriveStatusFromList(List<Map<String, Object>> slots) {
        if (slots == null || slots.isEmpty()) return "0";
        boolean anyManual = false;
        boolean allBound = true;
        boolean anyBound = false;
        for (Map<String, Object> s : slots) {
            if (Boolean.TRUE.equals(toBool(s.get("manual_ready")))) anyManual = true;
            boolean bound = s.get("paper_id") != null && !String.valueOf(s.get("paper_id")).isBlank();
            if (bound) anyBound = true; else allBound = false;
        }
        if (anyManual) return "2";
        if (allBound) return "2";
        return anyBound ? "1" : "0";
    }

    private Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    /**
     * 判定整体覆写是否使任一「原绑定」消失（slot 被删 / 其 paper_id 被清或换）——供 upsertLessons
     * 覆写时联动清 manual_ready（G5 反性，与 {@link #unbindSlot} 口径一致：绑定失效则手动覆盖失效）。
     * 纯新增 / 改名 / 改配方（原绑定逐一保留）→ 返回 false，不触发清态。
     *
     * @param oldSlotsJson DB 旧卷位 JSON（覆写前）
     * @param newSlots     覆写后的新卷位列表
     */
    public boolean anyBindingLost(String oldSlotsJson, List<Map<String, Object>> newSlots) {
        List<Map<String, Object>> oldSlots = parseSlots(oldSlotsJson);
        if (oldSlots.isEmpty()) return false;
        // 新卷位按 slot_seq 建 paper_id 索引
        Map<String, String> newBySeq = new LinkedHashMap<>();
        if (newSlots != null) {
            for (Map<String, Object> s : newSlots) {
                String seq = slotSeqKey(s);
                if (seq != null) newBySeq.put(seq, boundPaperId(s));
            }
        }
        for (Map<String, Object> old : oldSlots) {
            String oldPid = boundPaperId(old);
            if (oldPid == null) continue; // 原本未绑，谈不上丢失
            String seq = slotSeqKey(old);
            // slot 被删（seq 不在新集）或 paper_id 被清/换 → 原绑定消失
            if (seq == null || !newBySeq.containsKey(seq)) return true;
            if (!oldPid.equals(newBySeq.get(seq))) return true;
        }
        return false;
    }

    private String slotSeqKey(Map<String, Object> s) {
        Object v = s.get("slot_seq");
        return v == null ? null : String.valueOf(v).trim();
    }

    /** 卷位绑定的 paper_id（非空非空白才算绑定，否则 null）。 */
    private String boundPaperId(Map<String, Object> s) {
        Object v = s.get("paper_id");
        if (v == null || String.valueOf(v).isBlank()) return null;
        return String.valueOf(v).trim();
    }

    /**
     * 写入校验（upsert 卷位时）：name 必填非空；paper_id 若非空必须真实存在的卷。
     * slot 删除（更新时消失）不删卷=天然满足（卷是独立行，本方法不 cascade）。
     */
    public void validateSlots(List<Map<String, Object>> slots) {
        if (slots == null) return;
        for (Map<String, Object> s : slots) {
            Object name = s.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                throw new ServiceException("卷位 name 必填非空", 400);
            }
            Object pid = s.get("paper_id");
            if (pid != null && !String.valueOf(pid).isBlank()) {
                Long paperId = parsePaperId(pid);
                if (paperId == null || paperMapper.selectById(paperId) == null) {
                    throw new ServiceException("卷位绑定的卷不存在: paper_id=" + pid, 400);
                }
            }
        }
    }

    private Long parsePaperId(Object o) {
        try {
            return Long.valueOf(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ───────────────── 绑定 / 解绑 / 标记已备好（独立端点，逐个课次写库） ─────────────────

    /** 取课次（不存在抛 400）。 */
    private BizCoursePlanLesson requireLesson(Long lessonId) {
        BizCoursePlanLesson l = lessonId == null ? null : lessonMapper.selectById(lessonId);
        if (l == null) throw new ServiceException("课次不存在: lessonId=" + lessonId, 400);
        return l;
    }

    private Map<String, Object> requireSlot(List<Map<String, Object>> slots, Integer slotSeq) {
        if (slotSeq == null) throw new ServiceException("slotSeq 不能为空", 400);
        for (Map<String, Object> s : slots) {
            if (s.get("slot_seq") != null && String.valueOf(slotSeq).equals(String.valueOf(s.get("slot_seq")))) {
                return s;
            }
        }
        throw new ServiceException("卷位不存在: slotSeq=" + slotSeq, 400);
    }

    private void persist(BizCoursePlanLesson lesson, List<Map<String, Object>> slots) {
        BizCoursePlanLesson up = new BizCoursePlanLesson();
        up.setId(lesson.getId());
        up.setPaperSlots(JsonUtils.toJsonString(slots));
        lessonMapper.updateById(up);
    }

    /**
     * 绑定既有卷到卷位（D7 兜底：任意 mine 卷可挂）。校验卷存在且归属当前登录用户。
     * 不清 manual_ready（仅解绑清）。返回更新后的卷位列表。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> bindPaper(Long lessonId, Integer slotSeq, Long paperId, boolean checkOwner) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        List<Map<String, Object>> slots = parseSlots(lesson.getPaperSlots());
        Map<String, Object> slot = requireSlot(slots, slotSeq);
        if (paperId == null) throw new ServiceException("paperId 不能为空", 400);
        BizPaper paper = paperMapper.selectById(paperId);
        if (paper == null) throw new ServiceException("卷不存在: paperId=" + paperId, 400);
        if (checkOwner) {
            Long uid = LoginHelper.getUserId();
            if (uid == null || !String.valueOf(uid).equals(paper.getCreateBy())) {
                throw new ServiceException("只能挂本人创建的卷到卷位", 400);
            }
        }
        slot.put("paper_id", String.valueOf(paperId));
        persist(lesson, slots);
        return slots;
    }

    /**
     * 解绑卷位（paper_id 置 null，卷留库不删）。🔴 任一卷位解绑 → 全课次 manual_ready 自动清 false（G5 反性）。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> unbindSlot(Long lessonId, Integer slotSeq) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        List<Map<String, Object>> slots = parseSlots(lesson.getPaperSlots());
        Map<String, Object> slot = requireSlot(slots, slotSeq);
        slot.put("paper_id", null);
        // 解绑即清整课次手动覆盖（覆盖失效，态按 paper_id 重新推导）
        for (Map<String, Object> s : slots) s.put("manual_ready", false);
        persist(lesson, slots);
        return slots;
    }

    /**
     * 标记已备好 / 取消（manual_ready 覆盖，课次级：对全部卷位置位）。零卷位课次不可标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> markReady(Long lessonId, boolean ready) {
        BizCoursePlanLesson lesson = requireLesson(lessonId);
        List<Map<String, Object>> slots = parseSlots(lesson.getPaperSlots());
        if (slots.isEmpty()) throw new ServiceException("零卷位课次无法标记已备好，请先规划卷位", 400);
        for (Map<String, Object> s : slots) s.put("manual_ready", ready);
        persist(lesson, slots);
        return slots;
    }
}
