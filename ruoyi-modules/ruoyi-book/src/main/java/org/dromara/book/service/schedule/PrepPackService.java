package org.dromara.book.service.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.bo.PrepPackBo;
import org.dromara.book.domain.entity.BizPrepPack;
import org.dromara.book.domain.entity.BizScheduleSession;
import org.dromara.book.mapper.BizPrepPackMapper;
import org.dromara.book.mapper.BizScheduleSessionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 备课包（备课材料）Service（PRD-C-213 /teacher/schedule/prep-pack）。
 *
 * <p>1:1 绑课次或散课；render 全段拼一份 HTML、段间强制起新页，一次出<b>一份 PDF</b>
 * （BUG-010 单文件拍板，修订 D11「一段一卷」；段无题 → 400 整单不出半卷）。
 * artifacts 契约 = 单条 [{seg:"备课材料", file, pages}]；segIndex 单段重 render 入参
 * 保留兼容（单段时也是一个文件，seg=段名）。seg JSON 支持可选段内分组
 * groups:[{title, question_ids}]（BUG-004，FE 编辑 UI 不做、数据层/MCP 可写即可）。
 * R1b S2/S3 拍板：pack.status = 备课状态唯一权威（lesson.prep_state 已删列）；
 * session.prep_status 仅作日历色点缓存，随包状态联动。统一绑定解析 = 场次已绑课次
 * 一律归并 lesson 口径建/取包（一课一包闸，杜绝双包）。
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepPackService {

    private final BizPrepPackMapper packMapper;
    private final BizScheduleSessionMapper sessionMapper;

    /** 建包（1:1，已存在则返已有）。装配中 → session 缓存态 '1'。 */
    @Transactional(rollbackFor = Exception.class)
    public Long build(PrepPackBo bo) {
        if (bo.getPlanLessonId() == null && bo.getSessionId() == null) {
            throw new ServiceException("planLessonId 与 sessionId 至少传一个");
        }
        Long[] binding = resolveBinding(bo.getPlanLessonId(), bo.getSessionId());
        Long lessonId = binding[0];
        Long sessionId = binding[1];
        BizPrepPack existing = findByBinding(lessonId, sessionId);
        // 防双包校验（历史脏数据）：解析归并到 lesson 口径后，同场次还挂着 session 级包
        // → 返回 lesson 包并告警，不写自动合并（清库重灌后自然消解）。
        if (existing != null && lessonId != null && bo.getSessionId() != null) {
            BizPrepPack stale = packMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
                .eq(BizPrepPack::getSessionId, bo.getSessionId()));
            if (stale != null) {
                log.warn("[prep-pack] 双包脏数据：lesson 包 id={} 与 session 包 id={} 并存"
                        + "（sessionId={}, lessonId={}），返回 lesson 包，不自动合并",
                    existing.getId(), stale.getId(), bo.getSessionId(), lessonId);
                return existing.getId();
            }
        }
        BizPrepPack pack = existing == null ? new BizPrepPack() : existing;
        pack.setPlanLessonId(lessonId);
        pack.setSessionId(sessionId);
        if (bo.getSegs() != null) pack.setSegs(normalizeSegs(bo.getSegs()));
        if (existing == null) {
            pack.setStatus("0");
            packMapper.insert(pack);
        } else {
            packMapper.updateById(pack);
        }
        syncSessionPrepStatus(pack, "1");
        return pack.getId();
    }

    /**
     * 统一绑定解析（R1b S2 一课一包闸）：sessionId 非空且该场次已绑 plan_lesson_id →
     * 归并为 lessonId 口径（sessionId 置空），从场次入口建/取包不再产生 session 级双包；
     * 场次未绑课次（散课）才走 session 级口径。返回 [lessonId, sessionId]。
     */
    private Long[] resolveBinding(Long lessonId, Long sessionId) {
        if (lessonId == null && sessionId != null) {
            BizScheduleSession s = sessionMapper.selectById(sessionId);
            if (s != null && s.getPlanLessonId() != null) {
                return new Long[]{s.getPlanLessonId(), null};
            }
            return new Long[]{null, sessionId};
        }
        return new Long[]{lessonId, null};
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
            Long[] binding = resolveBinding(lessonId, sessionId);
            pack = findByBinding(binding[0], binding[1]);
            // 历史脏数据兜底：场次已绑课次但 lesson 级包不存在、旧 session 级包还在 → 回退取它
            if (pack == null && sessionId != null && binding[0] != null) {
                pack = packMapper.selectOne(new LambdaQueryWrapper<BizPrepPack>()
                    .eq(BizPrepPack::getSessionId, sessionId));
            }
        }
        if (pack == null) return null;
        return packVo(pack);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSegs(Long packId, Object segs) {
        BizPrepPack pack = packMapper.selectById(packId);
        if (pack == null) throw new ServiceException("备课包不存在");
        pack.setSegs(segs == null ? null : normalizeSegs(segs));
        packMapper.updateById(pack);
        syncSessionPrepStatus(pack, "1");
    }

    /**
     * R1b S3 收敛：备课状态唯一权威 = pack.status，本方法只回写 session.prep_status（日历色点缓存）。
     * pack 绑课次 → 该课次所有场次；散课 → 仅直挂场次。不再写 lesson（prep_state 已删列）。
     */
    private void syncSessionPrepStatus(BizPrepPack pack, String stateVal) {
        if (pack.getPlanLessonId() != null) {
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

    /**
     * segs 存储键名统一 snake_case（question_ids，含 groups 内层同规则）。收到 camelCase
     * questionIds 也转，修 biz_prep_pack.segs camelCase/snake_case 混存。返回规整后 JSON 串。
     */
    @SuppressWarnings("unchecked")
    private String normalizeSegs(Object segs) {
        List<Map<String, Object>> list = JsonUtils.getObjectMapper().convertValue(
            segs, new TypeReference<List<Map<String, Object>>>() {
            });
        if (list == null) return JsonUtils.toJsonString(segs);
        for (Map<String, Object> seg : list) {
            if (seg == null) continue;
            normalizeQidKey(seg);
            List<Map<String, Object>> groups = segGroups(seg);
            if (groups != null) {
                for (Map<String, Object> g : groups) normalizeQidKey(g);
            }
        }
        return JsonUtils.toJsonString(list);
    }

    private void normalizeQidKey(Map<String, Object> holder) {
        if (holder.containsKey("questionIds")) {
            Object v = holder.remove("questionIds");
            holder.putIfAbsent("question_ids", v);
        }
    }

    /** 段内分组（BUG-004 可选 groups:[{title, question_ids}]）；无有效分组返回 null。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> segGroups(Map<String, Object> seg) {
        Object v = seg.get("groups");
        if (!(v instanceof List<?> list) || list.isEmpty()) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out.isEmpty() ? null : out;
    }
}
