package org.dromara.admincommon.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * admin 端通用文件上传 Service —— OSS 直传 + image_asset 落库。
 *
 * <p>H1 卡补丁抽离：原逻辑在 H 卡 {@code AdminQuestionServiceImpl#adminUploadFile} 内联实现，
 * 抽离到 admin-common 模块后 H/A/B 卡共用。
 *
 * <p>典型调用方：
 * <ul>
 *   <li>H 卡题库：{@code uploadImage(file, "question", "stem", "admin", "admin-upload")}</li>
 *   <li>A 卡 AI 试卷生成：{@code uploadImage(file, "paper", "cover", "paper-gen", "paper-gen")}</li>
 *   <li>B 卡试卷管理：{@code uploadImage(file, "paper", "stem", "admin", "paper-admin")}</li>
 * </ul>
 *
 * @author backend-dev (H1 卡补丁 — admin-common 抽离)
 */
public interface IAdminFileUploadService {

    /**
     * admin 端通用图上传：multipart → OSS（aliyun 等）→ 写 image_asset → 返 {url, assetId}。
     *
     * <p>service 内做的事：
     * <ol>
     *   <li>校验 file 非空 + size ≤ 5MB</li>
     *   <li>解析扩展名 + 校验白名单 png/jpg/jpeg/webp（不在白名单抛 ServiceException）</li>
     *   <li>缓存 size / contentType / originalFilename 元数据（防 transferTo 后 multipart 临时文件被移走）</li>
     *   <li>构造 OSS key {@code <keyPrefix>/<YYYY-MM-dd>/<uuid>.<ext>}</li>
     *   <li>用 {@code Files.createTempFile + transferTo} 中转 → {@code OssClient.upload(Path,...)}
     *       走 transferManager.uploadFile（File-based），绕过 aws-sdk
     *       BlockingInputStreamAsyncRequestBody 的 subscribeTimeout race bug</li>
     *   <li>插 image_asset 行（src_url='admin-upload://&lt;oss-key&gt;' + url_hash=SHA-256(srcUrl)
     *       + entity_type / asset_kind / entity_ref 全参数化）</li>
     *   <li>返 {@code {url: ossUrl, assetId: <自增 id>}}</li>
     * </ol>
     *
     * <p>事务：方法挂 {@code @Transactional(rollbackFor = Exception.class)}。
     *
     * @param file       multipart 文件（必填；≤ 5MB；png/jpg/jpeg/webp）
     * @param entityType image_asset.entity_type（必填，非空；如 question / paper / ai-gen）
     * @param assetKind  image_asset.asset_kind（必填，非空；如 stem / answer / explain / cover）
     * @param entityRef  image_asset.entity_ref（必填，非空；如 admin / paper-gen / ai-gen — 来源标识）
     * @param keyPrefix  OSS key 顶层前缀（必填，非空；如 admin-upload / paper-gen / ai-gen）
     * @return {@code Map<"url"|"assetId", Object>}
     */
    Map<String, Object> uploadImage(MultipartFile file,
                                    String entityType,
                                    String assetKind,
                                    String entityRef,
                                    String keyPrefix);
}
