package org.dromara.admincommon.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.admincommon.mapper.AdminImageAssetWriteMapper;
import org.dromara.admincommon.service.IAdminFileUploadService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * admin 端通用文件上传 Service 实现（H1 卡补丁抽离）。
 *
 * <p>实现要点：详见 {@link IAdminFileUploadService#uploadImage} javadoc。
 *
 * <p>实现层关键 workaround（H1 卡 Bug A + A2 教训）：
 * <ul>
 *   <li>不走 {@code OssClient.upload(InputStream,...)} —— 有 aws-sdk
 *       {@code BlockingInputStreamAsyncRequestBody} 的 subscribeTimeout race bug</li>
 *   <li>{@code MultipartFile} 在 {@code transferTo} 后底层 undertow 临时文件被移走 —
 *       必须先把 fileSize / contentType / originalFilename 缓存到局部变量</li>
 * </ul>
 *
 * @author backend-dev (H1 卡补丁 — admin-common 抽离)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileUploadServiceImpl implements IAdminFileUploadService {

    private final AdminImageAssetWriteMapper imageAssetWriteMapper;

    /** 单文件上限 5MB。 */
    private static final long UPLOAD_MAX_BYTES = 5L * 1024 * 1024;

    /** 合法扩展名白名单（小写比较，含点）。 */
    private static final Set<String> ALLOWED_EXTS =
        new HashSet<>(Arrays.asList(".png", ".jpg", ".jpeg", ".webp"));

    /** OSS 路径日期段 — yyyy-MM-dd。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadImage(MultipartFile file,
                                           String entityType,
                                           String assetKind,
                                           String entityRef,
                                           String keyPrefix) {
        // 0. 入参非空校验
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (isBlank(entityType)) {
            throw new ServiceException("entityType 不能为空");
        }
        if (isBlank(assetKind)) {
            throw new ServiceException("assetKind 不能为空");
        }
        if (isBlank(entityRef)) {
            throw new ServiceException("entityRef 不能为空");
        }
        if (isBlank(keyPrefix)) {
            throw new ServiceException("keyPrefix 不能为空");
        }

        // 1. 缓存 multipart 元数据（防 transferTo 后底层临时文件被移走 — H1 Bug A2 教训）
        long fileSize = file.getSize();
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        if (fileSize > UPLOAD_MAX_BYTES) {
            throw new ServiceException("文件大小不能超过 5MB");
        }
        String suffix = resolveSuffix(originalFilename).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTS.contains(suffix)) {
            throw new ServiceException("仅支持 png/jpg/jpeg/webp 格式");
        }

        // 2. 构造 OSS key —— <keyPrefix>/<YYYY-MM-dd>/<uuid>.<ext>
        String datePath = LocalDate.now().format(DATE_FMT);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ossKey = keyPrefix.trim() + "/" + datePath + "/" + uuid + suffix;

        // 3. 上传 OSS —— 走 upload(Path,...)（File-based，绕过 aws-sdk BlockingInputStream race bug）
        OssClient storage = OssFactory.instance();
        UploadResult uploadResult;
        Path tempPath;
        try {
            tempPath = Files.createTempFile("admin-oss-", suffix);
            file.transferTo(tempPath.toFile());
        } catch (IOException e) {
            throw new ServiceException("临时文件创建失败：" + e.getMessage());
        }
        try {
            // OssClient.upload(Path) 内 finally 自动 FileUtils.del(tempPath)，本方法无需重复删
            uploadResult = storage.upload(tempPath, ossKey, null, contentType);
        } catch (Exception e) {
            throw new ServiceException("文件上传 OSS 失败：" + e.getMessage());
        }
        String ossUrl = uploadResult.getUrl();
        String host = extractHost(ossUrl);

        // 4. 写 image_asset（用上面缓存的 fileSize / contentType，不再触摸 multipart 临时文件）
        String srcUrl = "admin-upload://" + ossKey;
        String urlHash = sha256Hex(srcUrl);
        String extNoDot = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        Map<String, Object> idHolder = new HashMap<>();
        imageAssetWriteMapper.insertAdminUpload(
            entityType,
            assetKind,
            entityRef,
            srcUrl,
            urlHash,
            host,
            extNoDot,
            ossKey,
            "",
            fileSize,
            contentType,
            ossUrl,
            idHolder
        );
        Object assetIdObj = idHolder.get("id");
        if (assetIdObj == null) {
            throw new ServiceException("image_asset 落库失败");
        }
        Long assetId = ((Number) assetIdObj).longValue();

        // 5. 返 {url, assetId}
        Map<String, Object> data = new HashMap<>(2);
        data.put("url", ossUrl);
        data.put("assetId", assetId);
        return data;
    }

    // ────────────────────────────────────────────────────────────
    // 内部工具
    // ────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 从原始文件名解析后缀（含点）。无后缀返空串 — 调用方校验白名单时拒绝。
     */
    private static String resolveSuffix(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx < 0 || idx == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(idx);
    }

    /**
     * 从 OSS URL 解析 host（供 image_asset.host 字段）。解析失败返 null。
     */
    private static String extractHost(String ossUrl) {
        if (ossUrl == null || ossUrl.isEmpty()) {
            return null;
        }
        try {
            return URI.create(ossUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * SHA-256 hex（64 字符，匹配 image_asset.url_hash char(64) 约束）。
     */
    private static String sha256Hex(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("SHA-256 不可用：" + e.getMessage());
        }
    }
}
