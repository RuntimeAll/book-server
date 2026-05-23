package org.dromara.scheduling.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主线 C · 高德 Web 服务封装（骨架 Step 6）
 *
 * <p>2 个端点：
 * <ul>
 *   <li>POST /schedule/amap/geocode    {address} → {lng, lat, formatted_address, level}</li>
 *   <li>POST /schedule/amap/direction  {origin, destination, mode, city?} → {duration_sec, distance_m}</li>
 * </ul>
 *
 * <p>mode = driving / cycling / transit / walking. cycling 用 v4 bicycling（字段路径独立），其余 v3。
 * 打车 = driving 代算（2026-05-23 业务拍板）。
 *
 * <p>对应 spike S2 的 Java 端口。
 */
@RestController
@RequestMapping("/schedule/amap")
@RequiredArgsConstructor
public class ScheduleAmapController {

    private final ObjectMapper objectMapper;

    @Value("${schedule.amap.web-service-key:}")
    private String amapKey;

    private static final String BASE = "https://restapi.amap.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @SaIgnore
    @PostMapping("/geocode")
    public Map<String, Object> geocode(@RequestBody Map<String, String> body) throws Exception {
        if (amapKey == null || amapKey.isBlank()) {
            return Map.of("error", "AMAP_WEB_SERVICE_KEY 未配置");
        }
        String address = body == null ? null : body.get("address");
        if (address == null || address.isBlank()) {
            return Map.of("error", "address 不能为空");
        }
        String url = BASE + "/v3/geocode/geo?address="
                + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&key=" + amapKey;
        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        long dt = System.currentTimeMillis() - t0;
        if (resp.statusCode() != 200) {
            return Map.of("error", "Amap HTTP " + resp.statusCode(), "body", resp.body(), "elapsed_ms", dt);
        }
        Map<String, Object> d = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        if (!"1".equals(d.get("status"))) {
            return Map.of("error", "Amap status=" + d.get("status"), "info", d.get("info"), "elapsed_ms", dt);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> geocodes = (List<Map<String, Object>>) d.get("geocodes");
        if (geocodes == null || geocodes.isEmpty()) {
            return Map.of("error", "no geocode result for: " + address, "elapsed_ms", dt);
        }
        Map<String, Object> g = geocodes.get(0);
        String[] lngLat = ((String) g.get("location")).split(",");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("lng", lngLat[0]);
        out.put("lat", lngLat[1]);
        out.put("location", g.get("location"));
        out.put("formatted_address", g.get("formatted_address"));
        out.put("level", g.get("level"));
        out.put("elapsed_ms", dt);
        return out;
    }

    @SaIgnore
    @PostMapping("/direction")
    public Map<String, Object> direction(@RequestBody Map<String, String> body) throws Exception {
        if (amapKey == null || amapKey.isBlank()) {
            return Map.of("error", "AMAP_WEB_SERVICE_KEY 未配置");
        }
        String origin = body == null ? null : body.get("origin");
        String destination = body == null ? null : body.get("destination");
        String mode = body != null && body.getOrDefault("mode", "").isBlank() == false
                ? body.get("mode") : "cycling";
        String city = body != null && body.containsKey("city") ? body.get("city") : "北京";
        if (origin == null || destination == null || origin.isBlank() || destination.isBlank()) {
            return Map.of("error", "origin / destination 不能为空（lng,lat 格式）");
        }

        String urlPath;
        boolean isV4Cycling = false;
        switch (mode) {
            case "driving" -> urlPath = "/v3/direction/driving";
            case "cycling" -> { urlPath = "/v4/direction/bicycling"; isV4Cycling = true; }
            case "transit" -> urlPath = "/v3/direction/transit/integrated";
            case "walking" -> urlPath = "/v3/direction/walking";
            default -> { return Map.of("error", "unknown mode: " + mode + " (driving/cycling/transit/walking)"); }
        }

        StringBuilder url = new StringBuilder(BASE).append(urlPath)
                .append("?origin=").append(URLEncoder.encode(origin, StandardCharsets.UTF_8))
                .append("&destination=").append(URLEncoder.encode(destination, StandardCharsets.UTF_8))
                .append("&key=").append(amapKey);
        if ("transit".equals(mode)) {
            url.append("&city=").append(URLEncoder.encode(city, StandardCharsets.UTF_8));
        }

        long t0 = System.currentTimeMillis();
        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url.toString())).timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        long dt = System.currentTimeMillis() - t0;
        if (resp.statusCode() != 200) {
            return Map.of("error", "Amap HTTP " + resp.statusCode(), "body", resp.body(), "elapsed_ms", dt);
        }
        Map<String, Object> d = objectMapper.readValue(resp.body(), new TypeReference<>() {});
        boolean success = isV4Cycling
                ? Integer.valueOf(0).equals(d.get("errcode"))
                : "1".equals(d.get("status"));
        if (!success) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", isV4Cycling ? "Amap errcode=" + d.get("errcode") : "Amap status=" + d.get("status"));
            err.put("info", isV4Cycling ? d.get("errmsg") : d.get("info"));
            err.put("elapsed_ms", dt);
            return err;
        }

        Long durationSec;
        Long distanceM;
        if (isV4Cycling) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) d.get("data");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paths = (List<Map<String, Object>>) data.get("paths");
            Map<String, Object> p = paths.get(0);
            durationSec = ((Number) p.get("duration")).longValue();
            distanceM = ((Number) p.get("distance")).longValue();
        } else if ("transit".equals(mode)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> route = (Map<String, Object>) d.get("route");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transits = (List<Map<String, Object>>) route.get("transits");
            if (transits == null || transits.isEmpty()) {
                return Map.of("error", "no transit path", "elapsed_ms", dt);
            }
            Map<String, Object> t = transits.get(0);
            durationSec = Long.parseLong(t.get("duration").toString());
            distanceM = Long.parseLong(t.get("distance").toString());
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> route = (Map<String, Object>) d.get("route");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> paths = (List<Map<String, Object>>) route.get("paths");
            Map<String, Object> p = paths.get(0);
            durationSec = Long.parseLong(p.get("duration").toString());
            distanceM = Long.parseLong(p.get("distance").toString());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("mode", mode);
        out.put("duration_sec", durationSec);
        out.put("distance_m", distanceM);
        out.put("elapsed_ms", dt);
        return out;
    }
}
