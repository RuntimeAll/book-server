package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.SessionReviewBo;
import org.dromara.book.domain.entity.BizClass;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.domain.entity.BizPrepPack;
import org.dromara.book.domain.entity.BizSessionReview;
import org.dromara.book.domain.entity.BizStudent;
import org.dromara.book.mapper.BizClassMapper;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizPrepPackMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.mapper.BizSessionReviewMapper;
import org.dromara.book.mapper.BizStudentMapper;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课后回收 Service（PRD-C-213 /teacher/schedule/session/{id}/review）。
 *
 * <p>家长反馈消息模板拼装（段名映射：思维题→思维题 / 课内→同步 / 专项→拓展奥数）；错/卡 items 按 cause
 * 聚合成 error_signals（by=system,status=pending,session_id 溯源）append 进 profile_json；session 标已上；
 * 重复提交=覆盖并把上一版整体快照进 prev_json。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class SessionReviewService {

    private final BizSessionReviewMapper reviewMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizStudentMapper studentMapper;
    private final BizClassMapper classMapper;
    private final BizPrepPackMapper prepPackMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(Long sessionId, SessionReviewBo bo) {
        BizScheduleSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new ServiceException("场次不存在");
        BizCoursePlanLesson lesson = session.getPlanLessonId() == null ? null
            : lessonMapper.selectById(session.getPlanLessonId());

        List<Map<String, Object>> items = normItems(bo.getItemResults());

        // 家长反馈消息（override 也过内部词防线）；G12：内容优先取备课包实际段落
        String parentMsg;
        if (bo.getParentMsgOverride() != null && !bo.getParentMsgOverride().isBlank()) {
            parentMsg = ScheduleRenderUtil.stripInternalWords(bo.getParentMsgOverride());
        } else {
            parentMsg = buildParentMsg(lesson, resolvePackSegs(session), subjectOf(session));
        }

        // 肖像 delta（错/卡 按 cause 聚合）
        List<Map<String, Object>> delta = buildPortraitDelta(items, sessionId);
        appendToProfile(session, delta);

        // upsert review（重复=覆盖 + 上一版进 prev_json）
        BizSessionReview existing = reviewMapper.selectOne(new LambdaQueryWrapper<BizSessionReview>()
            .eq(BizSessionReview::getSessionId, sessionId));
        BizSessionReview review = existing == null ? new BizSessionReview() : existing;
        if (existing != null) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("itemResults", parseAny(existing.getItemResults()));
            snap.put("teacherNote", existing.getTeacherNote());
            snap.put("parentMsg", existing.getParentMsg());
            snap.put("portraitDelta", parseAny(existing.getPortraitDelta()));
            snap.put("version", existing.getVersion());
            review.setPrevJson(JsonUtils.toJsonString(snap));
            review.setVersion((existing.getVersion() == null ? 1 : existing.getVersion()) + 1);
        } else {
            review.setVersion(1);
        }
        review.setSessionId(sessionId);
        review.setItemResults(JsonUtils.toJsonString(items));
        review.setTeacherNote(bo.getTeacherNote());
        review.setParentMsg(parentMsg);
        review.setPortraitDelta(JsonUtils.toJsonString(delta));
        if (existing == null) reviewMapper.insert(review);
        else reviewMapper.updateById(review);

        // session 标已上
        session.setSessionStatus("1");
        sessionMapper.updateById(session);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("parentMsg", parentMsg);
        r.put("portraitDelta", delta);
        return r;
    }

    public Map<String, Object> get(Long sessionId) {
        BizSessionReview review = reviewMapper.selectOne(new LambdaQueryWrapper<BizSessionReview>()
            .eq(BizSessionReview::getSessionId, sessionId));
        if (review == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(review.getId()));
        m.put("sessionId", String.valueOf(review.getSessionId()));
        m.put("itemResults", parseAny(review.getItemResults()));
        m.put("teacherNote", review.getTeacherNote());
        m.put("parentMsg", review.getParentMsg());
        m.put("portraitDelta", parseAny(review.getPortraitDelta()));
        m.put("version", review.getVersion());
        m.put("createTime", review.getCreateTime());
        m.put("updateTime", review.getUpdateTime());
        return m;
    }

    // ─────────────────────── 家长消息模板 ───────────────────────

    /**
     * 家长反馈消息（G12/G13）。段内容 = 备课包实际段落优先（packSegs 的段名/topic），
     * 缺 topic 用 lesson.seg_template 对应段兜底；无包才全走 lesson.seg_template。
     * 学科泛化：subject 含"数学"输出"思维数学"，其他学科用 subject 原文，缺失回退"思维数学"。
     * 输出前过内部词防线。开头/三行格式固定不破 G13 断言。
     */
    private String buildParentMsg(BizCoursePlanLesson lesson, List<Map<String, Object>> packSegs, String subject) {
        String think = "思维热身";
        String tong = "";
        String zhan = "";
        // lesson.seg_template 作兜底源
        List<Map<String, Object>> tplSegs = lesson == null ? new ArrayList<>() : parseSegList(lesson.getSegTemplate());

        // 优先备课包实际段落
        for (Map<String, Object> seg : (packSegs != null && !packSegs.isEmpty()) ? packSegs : tplSegs) {
            String name = str(seg.get("name"));
            if (name == null) continue;
            String topic = str(seg.get("topic"));
            if ((topic == null || topic.isBlank())) {
                topic = tplTopic(tplSegs, name);   // pack 段无 topic → 用 lesson 模板对应段兜底
            }
            if (name.contains("思维")) {
                if (topic != null && !topic.isBlank()) think = topic;
            } else if (name.contains("课内") || name.contains("同步")) {
                if (topic != null && !topic.isBlank()) tong = topic;
            } else if (name.contains("专项") || name.contains("奥数")) {
                if (topic != null && !topic.isBlank()) zhan = topic;
            }
        }
        if (lesson != null) {
            if (zhan.isBlank() && lesson.getParentCopy() != null) zhan = lesson.getParentCopy();
            if (tong.isBlank() && lesson.getTitle() != null) tong = lesson.getTitle();
            if (zhan.isBlank() && lesson.getTitle() != null) zhan = lesson.getTitle();
        }

        String subjectPhrase = "思维数学";
        if (subject != null && !subject.isBlank() && !subject.contains("数学")) {
            subjectPhrase = subject;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("家长您好！今天").append(subjectPhrase).append("的上课内容:\n");
        sb.append("思维题：").append(ScheduleRenderUtil.stripInternalWords(think)).append("\n");
        sb.append("同步：").append(ScheduleRenderUtil.stripInternalWords(tong)).append("\n");
        sb.append("拓展奥数：").append(ScheduleRenderUtil.stripInternalWords(zhan));
        return sb.toString();
    }

    /** 从 lesson.seg_template 取与 packSeg 同类段的 topic 兜底。 */
    private String tplTopic(List<Map<String, Object>> tplSegs, String name) {
        for (Map<String, Object> seg : tplSegs) {
            String n = str(seg.get("name"));
            if (n == null) continue;
            boolean same = (name.contains("思维") && n.contains("思维"))
                || ((name.contains("课内") || name.contains("同步")) && (n.contains("课内") || n.contains("同步")))
                || ((name.contains("专项") || name.contains("奥数")) && (n.contains("专项") || n.contains("奥数")));
            if (same) {
                String t = str(seg.get("topic"));
                if (t != null && !t.isBlank()) return t;
            }
        }
        return null;
    }

    /** 解析该场次的备课包 segs：session 直挂优先，无则回退课次级包（复用 1bf8cf7 语义）。 */
    private List<Map<String, Object>> resolvePackSegs(BizScheduleSession session) {
        BizPrepPack pack = prepPackMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
            .eq(BizPrepPack::getSessionId, session.getId()));
        if (pack == null && session.getPlanLessonId() != null) {
            pack = prepPackMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
                .eq(BizPrepPack::getPlanLessonId, session.getPlanLessonId()));
        }
        if (pack == null) return new ArrayList<>();
        return parseSegList(pack.getSegs());
    }

    /** 取该场次对象的学科（学生/班级）。 */
    private String subjectOf(BizScheduleSession session) {
        if ("1".equals(session.getTargetType())) {
            BizClass c = classMapper.selectById(session.getTargetId());
            return c == null ? null : c.getSubject();
        }
        BizStudent s = studentMapper.selectById(session.getTargetId());
        return s == null ? null : s.getSubject();
    }

    // ─────────────────────── 肖像 delta ───────────────────────

    private List<Map<String, Object>> buildPortraitDelta(List<Map<String, Object>> items, Long sessionId) {
        String ts = LocalDate.now().toString();
        // 按 cause 聚合错/卡
        Map<String, List<String>> byCause = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            String result = str(it.get("result"));
            if (!"错".equals(result) && !"卡".equals(result)) continue;
            String cause = str(it.get("cause"));
            if (cause == null || cause.isBlank()) cause = "其他";
            String seq = str(it.get("seq"));
            String seg = str(it.get("seg"));
            String ev = (seg == null ? "" : seg) + (seq == null ? "" : ("第" + seq + "题")) + result;
            byCause.computeIfAbsent(cause, k -> new ArrayList<>()).add(ev);
        }
        List<Map<String, Object>> delta = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byCause.entrySet()) {
            Map<String, Object> sig = new LinkedHashMap<>();
            sig.put("tag", e.getKey());
            sig.put("evidence", String.join("、", e.getValue()));
            sig.put("session_id", sessionId);
            sig.put("ts", ts);
            sig.put("by", "system");
            sig.put("status", "pending");
            delta.add(sig);
        }
        return delta;
    }

    @SuppressWarnings("unchecked")
    private void appendToProfile(BizScheduleSession session, List<Map<String, Object>> delta) {
        if (delta.isEmpty()) return;
        boolean isClass = "1".equals(session.getTargetType());
        String profileJson = isClass
            ? classMapper.selectById(session.getTargetId()).getProfileJson()
            : studentMapper.selectById(session.getTargetId()).getProfileJson();
        Map<String, Object> profile = (profileJson == null || profileJson.isBlank())
            ? new LinkedHashMap<>() : JsonUtils.parseObject(profileJson, new TypeReference<>() {
        });
        if (profile == null) profile = new LinkedHashMap<>();
        Object sigObj = profile.get("error_signals");
        List<Object> signals = sigObj instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
        signals.addAll(delta);
        profile.put("error_signals", signals);
        String out = JsonUtils.toJsonString(profile);
        if (isClass) {
            BizClass c = classMapper.selectById(session.getTargetId());
            c.setProfileJson(out);
            classMapper.updateById(c);
        } else {
            BizStudent s = studentMapper.selectById(session.getTargetId());
            s.setProfileJson(out);
            studentMapper.updateById(s);
        }
    }

    // ─────────────────────── helpers ───────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normItems(List<Object> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object o : raw) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }

    private List<Map<String, Object>> parseSegList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        List<Map<String, Object>> segs = JsonUtils.parseObject(json, new TypeReference<>() {
        });
        return segs == null ? new ArrayList<>() : segs;
    }

    private Object parseAny(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonUtils.parseObject(json, Object.class);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
