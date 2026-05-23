package org.dromara.admincommon.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * admin 端通用文件上传 Service —— OSS 直传，返回 OSS URL。
 *
 * <p>H1 卡补丁抽离：原逻辑在 H 卡 {@code AdminQuestionServiceImpl#adminUploadFile} 内联实现，
 * 抽离到 admin-common 模块后 H/A/B 卡共用。
 *
 * <p>2026-05-23 设计调整：用户拍板 image_asset 表即将废弃，本 service <strong>不再写入
 * image_asset</strong>；OSS 真上传保留，只返 {@code {url}} 一字段 Map。业务表（如
 * biz_question.stem_img_url）直接落 OSS URL。
 *
 * <p>典型调用方：
 * <ul>
 *   <li>H 卡题库：{@code uploadImage(file, "question", "stem", "admin", "admin-upload")}</li>
 *   <li>A 卡 AI 试卷生成：{@code uploadImage(file, "paper", "cover", "paper-gen", "paper-gen")}</li>
 *   <li>B 卡试卷管理：{@code uploadImage(file, "paper", "stem", "admin", "paper-admin")}</li>
 * </ul>
 *
 * @author backend-dev (H1 卡补丁 — admin-common 抽离 + image_asset 废弃)
 */
public interface IAdminFileUploadService {

    /**
     * admin 端通用图上传：multipart → OSS（aliyun 等）→ 返 {url}。
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
     *   <li>返 {@code {url: ossUrl}}（image_asset 表 2026-05-23 废弃，不再落库）</li>
     * </ol>
     *
     * <p>事务：方法挂 {@code @Transactional(rollbackFor = Exception.class)}（OSS 自身无事务，
     * 但保留事务范围以兼容未来若 service 再加业务表写入）。
     *
     * <p>entityType / assetKind / entityRef 入参当前<strong>不落库</strong>，但保留供：
     * <ul>
     *   <li>OSS key 子目录扩展（未来按 entityType 分目录）</li>
     *   <li>切到新业务表（替代 image_asset）时复用</li>
     *   <li>service 内部日志埋点用于业务追溯</li>
     * </ul>
     *
     * @param file       multipart 文件（必填；≤ 5MB；png/jpg/jpeg/webp）
     * @param entityType 业务实体类型（必填，非空；如 question / paper / ai-gen）
     * @param assetKind  资源种类（必填，非空；如 stem / answer / explain / cover）
     * @param entityRef  来源标识（必填，非空；如 admin / paper-gen / ai-gen）
     * @param keyPrefix  OSS key 顶层前缀（必填，非空；如 admin-upload / paper-gen / ai-gen）
     * @return {@code Map<"url", String>} —— 调用方拿 OSS URL 直接存业务表
     */
    Map<String, Object> uploadImage(MultipartFile file,
                                    String entityType,
                                    String assetKind,
                                    String entityRef,
                                    String keyPrefix);
}
