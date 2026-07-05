package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.bo.PrepPackBo;
import org.dromara.book.domain.entity.BizCoursePlanLesson;
import org.dromara.book.domain.entity.BizPrepPack;
import org.dromara.book.domain.entity.BizQuestion;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.mapper.BizCoursePlanLessonMapper;
import org.dromara.book.mapper.BizPrepPackMapper;
import org.dromara.book.mapper.BizQuestionMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.book.util.ScheduleRenderUtil;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备课包 Service（PRD-C-213 /teacher/schedule/prep-pack）。
 *
 * <p>1:1 绑课次或散课；render 逐段出 PDF（段无题 → 400 整单不出半卷）；全段成功且 markReady →
 * pack '2' + lesson.prep_state '2' + session.prep_status '2' 双态联动。装配中 → 双态 '1'。
 *
 * @author backend-dev
 */
@Service
@RequiredArgsConstructor
public class PrepPackService {

    private final BizPrepPackMapper packMapper;
    private final BizCoursePlanLessonMapper lessonMapper;
    private final BizScheduleSessionMapper sessionMapper;
    private final BizQuestionMapper questionMapper;
    private final ScheduleRenderUtil renderUtil;

    /** 建包（1:1，已存在则返已有）。装配中 → 双态 '1'。 */
    @Transactional(rollbackFor = Exception.class)
    public Long build(PrepPackBo bo) {
        if (bo.getPlanLessonId() == null && bo.getSessionId() == null) {
            throw new ServiceException("planLessonId 与 sessionId 至少传一个");
        }
        BizPrepPack existing = findByBinding(bo.getPlanLessonId(), bo.getSessionId());
        BizPrepPack pack = existing == null ? new BizPrepPack() : existing;
        pack.setPlanLessonId(bo.getPlanLessonId());
        pack.setSessionId(bo.getSessionId());
        if (bo.getSegs() != null) pack.setSegs(JsonUtils.toJsonString(bo.getSegs()));
        if (existing == null) {
            pack.setStatus("0");
            packMapper.insert(pack);
        } else {
            packMapper.updateById(pack);
        }
        markDoubleState(pack, "1");
        return pack.getId();
    }

    private BizPrepPack findByBinding(Long lessonId, Long sessionId) {
        if (lessonId != null) {
            return packMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
                .eq(BizPrepPack::getPlanLessonId, lessonId));
        }
        return packMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
            .eq(BizPrepPack::getSessionId, sessionId));
    }

    public Map<String, Object> get(Long lessonId, Long sessionId, Long packId) {
        BizPrepPack pack;
        if (packId != null) {
            pack = packMapper.selectById(packId);
        } else {
            pack = findByBinding(lessonId, sessionId);
        }
        if (pack == null) return null;
        return packVo(pack);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSegs(Long packId, Object segs) {
        BizPrepPack pack = packMapper.selectById(packId);
        if (pack == null) throw new ServiceException("备课包不存在");
        pack.setSegs(segs == null ? null : JsonUtils.toJsonString(segs));
        packMapper.updateById(pack);
        markDoubleState(pack, "1");
    }

    /** 逐段渲染 PDF。段无题 → 400 报缺整单不出半卷；全段成功且 markReady → 双态 '2'。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> render(Long packId, Integer segIndex, boolean markReady) {
        BizPrepPack pack = packMapper.selectById(packId);
        if (pack == null) throw new ServiceException("备课包不存在");
        List<Map<String, Object>> segs = parseSegs(pack.getSegs());
        if (segs.isEmpty()) throw new ServiceException("备课包无分段");

        // 目标段
        List<Integer> targets = new ArrayList<>();
        if (segIndex != null) {
            if (segIndex < 0 || segIndex >= segs.size()) throw new ServiceException("段序号越界");
            targets.add(segIndex);
        } else {
            for (int i = 0; i < segs.size(); i++) targets.add(i);
        }

        // 🔴 先全量校验：任一段无题 → 整单 400，不出半卷
        for (int idx : targets) {
            Map<String, Object> seg = segs.get(idx);
            List<String> qids = qids(seg);
            if (qids.isEmpty()) {
                throw new ServiceException("第" + (idx + 1) + "段(" + segName(seg) + ")无题");
            }
        }

        // 渲染
        List<Map<String, Object>> artifacts = new ArrayList<>();
        for (int idx : targets) {
            Map<String, Object> seg = segs.get(idx);
            List<String> qids = qids(seg);
            List<Map<String, Object>> questions = loadQuestions(qids);
            if (questions.isEmpty()) {
                throw new ServiceException("第" + (idx + 1) + "段(" + segName(seg) + ")题目未找到");
            }
            String html = renderUtil.buildPrepSegHtml(
                segName(seg), str(seg.get("style")), str(seg.get("topic")),
                str(seg.get("note")), str(seg.get("rules")), questions);
            ScheduleRenderUtil.PdfResult res = renderUtil.printToPdf(html, "prep_" + packId + "_seg" + idx);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("seg", segName(seg));
            a.put("file", res.getFile());
            a.put("pages", res.getPages());
            a.put("url", "/teacher/schedule/artifact?path=" + res.getFile());
            artifacts.add(a);
        }

        pack.setArtifacts(JsonUtils.toJsonString(artifacts));
        boolean fullSuccess = segIndex == null;
        if (fullSuccess && markReady) {
            pack.setStatus("2");
            packMapper.updateById(pack);
            markDoubleState(pack, "2");
        } else {
            pack.setStatus("1");
            packMapper.updateById(pack);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("artifacts", artifacts);
        return r;
    }

    /** 双态联动：pack 绑课次 → lesson.prep_state + 该课次所有 session.prep_status；散课 → 仅 session。 */
    private void markDoubleState(BizPrepPack pack, String stateVal) {
        if (pack.getPlanLessonId() != null) {
            BizCoursePlanLesson l = lessonMapper.selectById(pack.getPlanLessonId());
            if (l != null) {
                l.setPrepState(stateVal);
                lessonMapper.updateById(l);
            }
            List<BizScheduleSession> ss = sessionMapper.selectList(new LambdaQueryWrapper<BizScheduleSession>()
                .eq(BizScheduleSession::getPlanLessonId, pack.getPlanLessonId()));
            for (BizScheduleSession s : ss) {
                s.setPrepStatus(stateVal);
                sessionMapper.updateById(s);
            }
        } else if (pack.getSessionId() != null) {
            BizScheduleSession s = sessionMapper.selectById(pack.getSessionId());
            if (s != null) {
                s.setPrepStatus(stateVal);
                sessionMapper.updateById(s);
            }
        }
    }

    /** 按 question_ids 顺序取 {id, stem, star}。stem=biz_question.stem_text 原样。 */
    private List<Map<String, Object>> loadQuestions(List<String> qids) {
        List<Long> ids = new ArrayList<>();
        for (String q : qids) {
            try { ids.add(Long.parseLong(q.trim())); } catch (Exception ignore) { }
        }
        if (ids.isEmpty()) return new ArrayList<>();
        List<Map<String, Object>> rows = questionMapper.selectMaps(new QueryWrapper<BizQuestion>()
            .select("id", "stem_text", "star_level").in("id", ids));
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = Long.valueOf(String.valueOf(row.get("id")));
            byId.put(id, row);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> row = byId.get(id);
            if (row == null) continue;
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", String.valueOf(id));
            q.put("stem", row.get("stem_text"));
            q.put("star", row.get("star_level"));
            out.add(q);
        }
        return out;
    }

    private Map<String, Object> packVo(BizPrepPack pack) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(pack.getId()));
        m.put("planLessonId", pack.getPlanLessonId() == null ? null : String.valueOf(pack.getPlanLessonId()));
        m.put("sessionId", pack.getSessionId() == null ? null : String.valueOf(pack.getSessionId()));
        m.put("segs", pack.getSegs() == null ? null : JsonUtils.parseObject(pack.getSegs(), Object.class));
        m.put("artifacts", pack.getArtifacts() == null ? null : JsonUtils.parseObject(pack.getArtifacts(), Object.class));
        m.put("status", pack.getStatus());
        m.put("createTime", pack.getCreateTime());
        m.put("updateTime", pack.getUpdateTime());
        return m;
    }

    private List<Map<String, Object>> parseSegs(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        List<Map<String, Object>> segs = JsonUtils.parseObject(json, new TypeReference<>() {
        });
        return segs == null ? new ArrayList<>() : segs;
    }

    @SuppressWarnings("unchecked")
    private List<String> qids(Map<String, Object> seg) {
        Object v = seg.get("question_ids");
        if (v == null) v = seg.get("questionIds");
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
        }
        return out;
    }

    private String segName(Map<String, Object> seg) {
        Object n = seg.get("name");
        return n == null ? "未命名段" : String.valueOf(n);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
