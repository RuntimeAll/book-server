package org.dromara.scheduling.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.scheduling.domain.BizTsBase;
import org.dromara.scheduling.mapper.BizTsBaseMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 主线 C · V-2 一站式 plan 计算
 *
 * <p>POST /schedule/plan/extract  {userMessage} → 一站式 plan
 *
 * <p>编排：
 * <pre>
 *   ① opus extract_lessons (chatController.extract)
 *   ② stage 分支:
 *        out_of_mvp_scope 非空 → stage="out_of_scope"
 *        missing_fields 非空  → stage="need_clarify"
 *        否则                  → stage="plan" 继续
 *   ③ 取基点 (baseMapper / user_id=1) → 没设 400 NO_BASE
 *   ④ 并行 geocode N 个 lesson.location
 *   ⑤ 并行 direction 算 N+1 段 (base→l0 / l0→l1 / ... / lN-1→base) cycling
 *   ⑥ 段算法: gap / margin / reachable / tolerance_min=10
 *   ⑦ 闭环: 所有段 reachable → closed=true
 * </pre>
 *
 * <p>MVP 简化:
 * <ul>
 *   <li>user_id 固定 1L</li>
 *   <li>出行方式锁 cycling</li>
 *   <li>段 0 / 段 N gap_min=null reachable=true (不约束出发/回家时间)</li>
 * </ul>
 */
@RestController
@RequestMapping("/schedule/plan")
@RequiredArgsConstructor
public class SchedulePlanController {

    private static final Long MVP_FIXED_USER_ID = 1L;
    private static final int TOLERANCE_MIN = 10;
    private static final String MODE = "cycling";

    private final ScheduleChatController chatController;
    private final ScheduleAmapController amapController;
    private final BizTsBaseMapper baseMapper;

    @SuppressWarnings("unchecked")
    @SaIgnore
    @PostMapping("/extract")
    public Map<String, Object> extractPlan(@RequestBody Map<String, String> body) throws Exception {
        long t0 = System.currentTimeMillis();
        String userMessage = body == null ? null : body.get("userMessage");
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of("ok", false, "error", "userMessage 不能为空");
        }

        // ① opus extract
        Map<String, Object> chatResp = chatController.extract(Map.of("userMessage", userMessage));
        if (chatResp.containsKey("error")) {
            Map<String, Object> err = new LinkedHashMap<>(chatResp);
            err.put("ok", false);
            err.put("stage", "extract_failed");
            return err;
        }
        Map<String, Object> extracted = (Map<String, Object>) chatResp.get("extracted");
        List<String> missing = (List<String>) extracted.getOrDefault("missing_fields", List.of());
        List<String> outOfScope = (List<String>) extracted.getOrDefault("out_of_mvp_scope", List.of());
        List<Map<String, Object>> lessons = (List<Map<String, Object>>) extracted.getOrDefault("lessons", List.of());

        // ② stage 分支
        if (outOfScope != null && !outOfScope.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("stage", "out_of_scope");
            out.put("extracted", extracted);
            out.put("message", "MVP 暂不支持跨天 / 周期规则。请描述某一天的课。 (" + String.join("; ", outOfScope) + ")");
            out.put("elapsed_ms", System.currentTimeMillis() - t0);
            return out;
        }
        if (missing != null && !missing.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("stage", "need_clarify");
            out.put("extracted", extracted);
            out.put("clarify_question", "请补充以下字段后重新提交: " + String.join(", ", missing));
            out.put("elapsed_ms", System.currentTimeMillis() - t0);
            return out;
        }
        if (lessons == null || lessons.isEmpty()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("stage", "need_clarify");
            out.put("extracted", extracted);
            out.put("clarify_question", "未识别出任何课程，请重新描述。");
            out.put("elapsed_ms", System.currentTimeMillis() - t0);
            return out;
        }

        // ③ 取基点
        BizTsBase base = baseMapper.selectOne(
            new LambdaQueryWrapper<BizTsBase>().eq(BizTsBase::getUserId, MVP_FIXED_USER_ID)
        );
        if (base == null) {
            return Map.of(
                "ok", false,
                "stage", "no_base",
                "error", "未设全局基点，请先 POST /schedule/base",
                "code", "NO_BASE"
            );
        }

        // ④ 并行 geocode 每节课
        int N = lessons.size();
        @SuppressWarnings("rawtypes")
        CompletableFuture<Map>[] geoFutures = new CompletableFuture[N];
        for (int i = 0; i < N; i++) {
            String loc = (String) lessons.get(i).get("location");
            geoFutures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    return amapController.geocode(Map.of("address", loc));
                } catch (Exception e) {
                    return Map.of("error", "geocode exception: " + e.getMessage());
                }
            });
        }
        CompletableFuture.allOf(geoFutures).join();

        // 解析 geocode 结果到 lessons 字段
        List<Map<String, Object>> lessonsOut = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Map<String, Object> l = lessons.get(i);
            Map<String, Object> geo = geoFutures[i].get();
            String lng = (String) geo.get("lng");
            String lat = (String) geo.get("lat");
            String formatted = (String) geo.get("formatted_address");
            if (lng == null || lat == null) {
                return Map.of(
                    "ok", false,
                    "stage", "geocode_failed",
                    "error", "课 " + i + " 地点 '" + l.get("location") + "' geocode 失败: " + geo.get("error"),
                    "extracted", extracted
                );
            }
            String startTime = (String) l.get("start_time");
            String date = (String) l.get("date");
            Integer durationMin = ((Number) l.get("duration_min")).intValue();
            LocalTime startLt = LocalTime.parse(startTime.length() == 5 ? startTime : startTime.substring(0, 5));
            LocalTime endLt = startLt.plusMinutes(durationMin);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("index", i);
            out.put("date", date);
            out.put("start_time", startLt.format(DateTimeFormatter.ofPattern("HH:mm")));
            out.put("end_time", endLt.format(DateTimeFormatter.ofPattern("HH:mm")));
            out.put("duration_min", durationMin);
            out.put("location_text", l.get("location"));
            out.put("lng", lng);
            out.put("lat", lat);
            out.put("geocoded_address", formatted);
            out.put("lesson_name", l.get("lesson_name"));
            lessonsOut.add(out);
        }

        // ⑤ 并行 direction 算 N+1 段
        String baseCoord = base.getLng().toPlainString() + "," + base.getLat().toPlainString();
        @SuppressWarnings("rawtypes")
        CompletableFuture<Map>[] dirFutures = new CompletableFuture[N + 1];
        for (int i = 0; i <= N; i++) {
            final String origin;
            final String dest;
            if (i == 0) {
                origin = baseCoord;
                dest = lessonsOut.get(0).get("lng") + "," + lessonsOut.get(0).get("lat");
            } else if (i == N) {
                origin = lessonsOut.get(N - 1).get("lng") + "," + lessonsOut.get(N - 1).get("lat");
                dest = baseCoord;
            } else {
                origin = lessonsOut.get(i - 1).get("lng") + "," + lessonsOut.get(i - 1).get("lat");
                dest = lessonsOut.get(i).get("lng") + "," + lessonsOut.get(i).get("lat");
            }
            dirFutures[i] = CompletableFuture.supplyAsync(() -> {
                try {
                    return amapController.direction(Map.of(
                        "origin", origin,
                        "destination", dest,
                        "mode", MODE,
                        "city", "杭州"
                    ));
                } catch (Exception e) {
                    return Map.of("error", "direction exception: " + e.getMessage());
                }
            });
        }
        CompletableFuture.allOf(dirFutures).join();

        // ⑥ 段算法 + ⑦ 闭环
        List<Map<String, Object>> segments = new ArrayList<>();
        boolean allReachable = true;
        for (int i = 0; i <= N; i++) {
            Map<String, Object> dir = dirFutures[i].get();
            if (dir.get("duration_sec") == null) {
                return Map.of(
                    "ok", false,
                    "stage", "direction_failed",
                    "error", "段 " + i + " direction 失败: " + dir.get("error"),
                    "extracted", extracted
                );
            }
            long durationSec = ((Number) dir.get("duration_sec")).longValue();
            long distanceM = ((Number) dir.get("distance_m")).longValue();

            Map<String, Object> seg = new LinkedHashMap<>();
            seg.put("index", i);

            Map<String, Object> from = new LinkedHashMap<>();
            Map<String, Object> to = new LinkedHashMap<>();
            if (i == 0) {
                from.put("type", "base");
                from.put("label", base.getAddressText());
                from.put("lng", base.getLng().toPlainString());
                from.put("lat", base.getLat().toPlainString());
                to.put("type", "lesson");
                to.put("label", lessonsOut.get(0).get("location_text"));
                to.put("lessonIndex", 0);
                to.put("lng", lessonsOut.get(0).get("lng"));
                to.put("lat", lessonsOut.get(0).get("lat"));
            } else if (i == N) {
                from.put("type", "lesson");
                from.put("label", lessonsOut.get(N - 1).get("location_text"));
                from.put("lessonIndex", N - 1);
                from.put("lng", lessonsOut.get(N - 1).get("lng"));
                from.put("lat", lessonsOut.get(N - 1).get("lat"));
                to.put("type", "base");
                to.put("label", base.getAddressText());
                to.put("lng", base.getLng().toPlainString());
                to.put("lat", base.getLat().toPlainString());
            } else {
                from.put("type", "lesson");
                from.put("label", lessonsOut.get(i - 1).get("location_text"));
                from.put("lessonIndex", i - 1);
                from.put("lng", lessonsOut.get(i - 1).get("lng"));
                from.put("lat", lessonsOut.get(i - 1).get("lat"));
                to.put("type", "lesson");
                to.put("label", lessonsOut.get(i).get("location_text"));
                to.put("lessonIndex", i);
                to.put("lng", lessonsOut.get(i).get("lng"));
                to.put("lat", lessonsOut.get(i).get("lat"));
            }
            seg.put("from", from);
            seg.put("to", to);
            seg.put("mode", MODE);
            seg.put("duration_sec", durationSec);
            seg.put("distance_m", distanceM);

            // gap / margin / reachable
            if (i == 0 || i == N) {
                seg.put("gap_min", null);
                seg.put("margin_min", null);
                seg.put("reachable", true);
            } else {
                LocalTime prevEnd = LocalTime.parse((String) lessonsOut.get(i - 1).get("end_time"));
                LocalTime curStart = LocalTime.parse((String) lessonsOut.get(i).get("start_time"));
                long gapMin = java.time.Duration.between(prevEnd, curStart).toMinutes();
                long durMin = durationSec / 60;
                long marginMin = gapMin - durMin - TOLERANCE_MIN;
                boolean reachable = marginMin >= 0;
                seg.put("gap_min", gapMin);
                seg.put("margin_min", marginMin);
                seg.put("reachable", reachable);
                if (!reachable) allReachable = false;
            }
            seg.put("tolerance_min", TOLERANCE_MIN);
            segments.add(seg);
        }

        // 组 plan
        Map<String, Object> baseBlock = new LinkedHashMap<>();
        baseBlock.put("label", base.getAddressText());
        baseBlock.put("lng", base.getLng().toPlainString());
        baseBlock.put("lat", base.getLat().toPlainString());
        baseBlock.put("formatted", base.getFormattedAddress());

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("date", lessonsOut.get(0).get("date"));
        plan.put("base", baseBlock);
        plan.put("lessons", lessonsOut);
        plan.put("segments", segments);
        plan.put("closed", allReachable);
        plan.put("elapsed_ms", System.currentTimeMillis() - t0);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("stage", "plan");
        resp.put("extracted", extracted);
        resp.put("plan", plan);
        return resp;
    }
}
