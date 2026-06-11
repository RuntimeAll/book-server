package org.dromara.book.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * OSS 题图拉取公共组件（PRD-A-014 抽离 ImageProxyController 取图逻辑）。
 *
 * <p>统一 SSRF 白名单（只允许 ai-book bucket）+ Redis 24h 缓存（Base64 String，
 * 避开 byte[] 经 Jackson codec 反序列化 ClassCastException 老坑）+ 体积上限。
 * 供 {@link org.dromara.book.controller.ImageProxyController}（FE 同源代理）
 * 与 {@code PdfComposer}（导出拼图）共用，单一 SSRF 把关点。
 *
 * @author backend-dev
 */
@Slf4j
@Component
public class OssImageFetcher {

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

    /** 拉取结果（字节 + content-type） */
    public record Fetched(byte[] bytes, String contentType) {
    }

    /**
     * 校验 url 是否在 OSS 白名单内（host == ai-book bucket）。
     *
     * @param url 待校验 URL
     * @return true = 合法可拉
     */
    public boolean isWhitelisted(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return OSS_HOST_WHITELIST.equals(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 拉取 OSS 图（Redis 缓存命中优先），白名单校验失败 / 拉取失败抛 {@link ServiceException}。
     *
     * @param url OSS 图 URL（必须 ai-book host）
     * @return 字节 + content-type
     */
    public Fetched fetch(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("非法 image url: " + url);
        }
        if (uri.getHost() == null || !OSS_HOST_WHITELIST.equals(uri.getHost())) {
            throw new ServiceException("非法 image host: " + uri.getHost());
        }

        // Redis 缓存命中（存 Base64 String，配合 Jackson codec）
        String cacheKey = CACHE_KEY_PREFIX + DigestUtils.md5DigestAsHex(url.getBytes(StandardCharsets.UTF_8));
        try {
            String cachedB64 = RedisUtils.getCacheObject(cacheKey);
            if (cachedB64 != null) {
                return new Fetched(Base64.getDecoder().decode(cachedB64), "image/png");
            }
        } catch (Exception e) {
            log.warn("[oss-image-fetcher] cache read failed, re-fetch: {} ({})", url, e.getMessage());
        }

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

        RedisUtils.setCacheObject(cacheKey, Base64.getEncoder().encodeToString(bytes), CACHE_TTL);
        return new Fetched(bytes, contentType);
    }
}
