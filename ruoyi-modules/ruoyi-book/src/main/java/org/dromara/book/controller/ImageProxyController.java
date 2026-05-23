package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Q' 卡 hotfix-4 — OSS 图代理端点。
 *
 * <p><b>背景</b>：ai-book OSS bucket 配了 {@code x-oss-force-download: true} 触发响应头
 * {@code Content-Disposition: attachment}。chrome 在 CORS 严格模式下
 * （{@code <img crossorigin="anonymous">}）见 attachment 视为非法 image resource → block。
 * 这导致预览模态加载题图被拒，PDF 截图也连带空白。
 *
 * <p><b>方案</b>：BE 代理拉 OSS 流，剥掉 {@code Content-Disposition} + {@code x-oss-force-download}
 * 后同源返给 FE。同时走 Redis 24h 缓存，OSS 单图只首次拉一次，后续命中 Redis。
 *
 * <p><b>调用</b>：{@code GET /api/teacher/image-proxy?url=<encoded-oss-url>}
 *
 * <p><b>安全</b>：
 * <ul>
 *   <li>{@code @SaCheckLogin} 沿用 teacher 鉴权（未登录不可下图）</li>
 *   <li>白名单只允许 ai-book OSS host，防 SSRF</li>
 *   <li>体积上限 10MB 防 DoS</li>
 * </ul>
 *
 * <p><b>envelope</b>：路径含 /teacher/ 前缀命中 {@link MisiktEnvelopeAdvice}，
 * 但 advice 实现已豁免非 JSON 响应（line 83-86），binary image 直接返不被包。
 *
 * @author backend-dev (Q' hotfix-4)
 */
@RestController
@RequestMapping("/teacher/image-proxy")
@RequiredArgsConstructor
@Slf4j
public class ImageProxyController {

    /** OSS host 白名单（防 SSRF）— 只允许代理 ai-book bucket */
    private static final String OSS_HOST_WHITELIST = "ai-book.oss-cn-hangzhou.aliyuncs.com";

    /** Redis 缓存 key 前缀 */
    private static final String CACHE_KEY_PREFIX = "img-proxy:";

    /** Redis 缓存 TTL — 题图变更频率低，24h 合理 */
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /** 单图体积上限 — 防 DoS / Redis 内存爆炸 */
    private static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;

    /** HttpClient 拉 OSS 超时 */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    /** java.net.http.HttpClient 单例（线程安全，复用连接池） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    @SaCheckLogin
    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam("url") String url) {
        // 1. URI 解析 + 白名单校验
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("非法 image url: " + url);
        }
        if (uri.getHost() == null || !OSS_HOST_WHITELIST.equals(uri.getHost())) {
            throw new ServiceException("非法 image host: " + uri.getHost());
        }

        // 2. Redis 缓存命中
        String cacheKey = CACHE_KEY_PREFIX + DigestUtils.md5DigestAsHex(url.getBytes(StandardCharsets.UTF_8));
        byte[] cached = RedisUtils.getCacheObject(cacheKey);
        if (cached != null) {
            log.debug("[image-proxy] cache hit: {}", url);
            return buildResponse(cached, "image/png");
        }

        // 3. 走 OSS 拉
        byte[] bytes;
        String contentType;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(FETCH_TIMEOUT)
                .GET()
                .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() != 200) {
                throw new ServiceException("OSS 拉取失败 status=" + resp.statusCode());
            }
            bytes = resp.body();
            if (bytes.length > MAX_IMAGE_SIZE) {
                throw new ServiceException("图片体积超限 " + bytes.length + " > " + MAX_IMAGE_SIZE);
            }
            contentType = resp.headers().firstValue("Content-Type").orElse("image/png");
        } catch (IOException e) {
            throw new ServiceException("OSS 拉取 IO 异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("OSS 拉取被中断");
        }

        // 4. 写 Redis 缓存
        RedisUtils.setCacheObject(cacheKey, bytes, CACHE_TTL);
        log.debug("[image-proxy] cache miss → fetched + cached: {} ({}B)", url, bytes.length);

        return buildResponse(bytes, contentType);
    }

    /**
     * 构造同源 image response。
     * 关键：不返 Content-Disposition 头 → chrome 当 inline image 渲染。
     */
    private ResponseEntity<byte[]> buildResponse(byte[] bytes, String contentType) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header("Cache-Control", "public, max-age=86400")
            .body(bytes);
    }
}
