package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.service.OssImageFetcher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Q' 卡 hotfix-4 — OSS 图代理端点。
 *
 * <p><b>背景</b>：ai-book OSS bucket 配了 {@code x-oss-force-download: true} 触发响应头
 * {@code Content-Disposition: attachment}。chrome 在 CORS 严格模式下
 * （{@code <img crossorigin="anonymous">}）见 attachment 视为非法 image resource → block。
 * 这导致预览模态加载题图被拒，PDF 截图也连带空白。
 *
 * <p><b>方案</b>：BE 代理拉 OSS 流，剥掉 {@code Content-Disposition} + {@code x-oss-force-download}
 * 后同源返给 FE。SSRF 白名单 + Redis 24h 缓存 + 体积上限的取图逻辑（PRD-A-014）已抽到
 * {@link OssImageFetcher} 公共组件（导出 PdfComposer 共用同一把关点），本控制器仅负责同源 HTTP 响应。
 *
 * <p><b>调用</b>：{@code GET /api/teacher/image-proxy?url=<encoded-oss-url>}
 *
 * <p><b>安全</b>：{@code @SaIgnore} 无需登录（chrome {@code <img src>} 不带 Bearer token，
 * 加鉴权会 401 阻塞 inline 渲染；OSS 图本身 public-read，代理层鉴权冗余）；SSRF 白名单 +
 * 体积上限在 OssImageFetcher 内。
 *
 * @author backend-dev (Q' hotfix-4 / PRD-A-014 抽离取图逻辑)
 */
@RestController
@RequestMapping("/teacher/image-proxy")
@RequiredArgsConstructor
@Slf4j
public class ImageProxyController {

    private final OssImageFetcher ossImageFetcher;

    @SaIgnore
    @GetMapping
    public ResponseEntity<byte[]> proxy(@RequestParam("url") String url) {
        OssImageFetcher.Fetched fetched = ossImageFetcher.fetch(url);
        // 关键：不返 Content-Disposition 头 → chrome 当 inline image 渲染
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(fetched.contentType()))
            .header("Cache-Control", "public, max-age=86400")
            .body(fetched.bytes());
    }
}
