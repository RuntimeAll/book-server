package org.dromara.admincommon.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.admincommon.service.IAdminFileUploadService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * admin 端通用文件上传 Service 实现 —— 委托 RuoYi 自带 {@link ISysOssService#upload(MultipartFile)}。
 *
 * <p>2026-05-23 演进：
 * <ol>
 *   <li>H1 卡补丁初版：内联实现 OSS upload + INSERT image_asset（commit fecc1a0）</li>
 *   <li>当日下午 image_asset 表废弃：删 image_asset 写入，仅 OSS 上传（commit 427f247）</li>
 *   <li>本次：sys_oss 接手追溯职责，admin 上传委托 RuoYi 自带 ISysOssService.upload —
 *       零重复代码 + 直接复用 RuoYi 后台 {@code /system/oss/list} 页面看历史上传</li>
 * </ol>
 *
 * <p>仍保留的业务层职责：
 * <ul>
 *   <li>5MB 上限校验（RuoYi 自带 upload 不限制大小）</li>
 *   <li>png/jpg/jpeg/webp 白名单校验（RuoYi 自带不限格式 — 题图业务约束）</li>
 *   <li>entityType / assetKind / entityRef / keyPrefix 入参<strong>不</strong>传给 RuoYi
 *       （RuoYi 用自己的 OSS prefix 逻辑），只做 INFO 日志埋点用于业务追溯</li>
 * </ul>
 *
 * <p>⚠️ Bug A race 风险提示：RuoYi {@code ISysOssService.upload} 内部走
 * {@code OssClient.uploadSuffix(byte[],...)} → {@code upload(InputStream,...)} —
 * 这条路径在 aws-sdk + aliyun 冷启动慢的情况偶发 120s race（H1 卡 Bug A 教训）。
 * dev / prod 冷启动首次上传如果碰到，重试一次基本会 OK（OssClient 已 cache）。
 *
 * @author backend-dev (H1 卡补丁 — 委托 RuoYi sys_oss)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileUploadServiceImpl implements IAdminFileUploadService {

    private final ISysOssService sysOssService;

    /** 单文件上限 5MB。 */
    private static final long UPLOAD_MAX_BYTES = 5L * 1024 * 1024;

    /** 合法扩展名白名单（小写比较，含点）。 */
    private static final Set<String> ALLOWED_EXTS =
        new HashSet<>(Arrays.asList(".png", ".jpg", ".jpeg", ".webp"));

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

        // 1. 校验文件大小 + 扩展名（RuoYi 自带 upload 不做这俩校验，业务约束 admin-common 内置）
        long fileSize = file.getSize();
        if (fileSize > UPLOAD_MAX_BYTES) {
            throw new ServiceException("文件大小不能超过 5MB");
        }
        String suffix = resolveSuffix(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTS.contains(suffix)) {
            throw new ServiceException("仅支持 png/jpg/jpeg/webp 格式");
        }

        // 2. 委托 RuoYi 自带 ISysOssService.upload — 内部完成 OSS 真上传 + sys_oss 落库
        SysOssVo vo;
        try {
            vo = sysOssService.upload(file);
        } catch (Exception e) {
            throw new ServiceException("文件上传失败：" + e.getMessage());
        }

        // 3. 业务追溯日志埋点（entityType/assetKind/entityRef 不落 sys_oss，仅日志可查）
        log.info("admin OSS 上传成功 entityType={} assetKind={} entityRef={} keyPrefix={} ossId={} size={} url={}",
            entityType, assetKind, entityRef, keyPrefix, vo.getOssId(), fileSize, vo.getUrl());

        // 4. 返 {url}
        Map<String, Object> data = new HashMap<>(1);
        data.put("url", vo.getUrl());
        return data;
    }

    // ────────────────────────────────────────────────────────────
    // 内部工具
    // ────────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 从原始文件名解析后缀（含点）。无后缀返空串 — 由后续白名单校验拒绝。
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
}
