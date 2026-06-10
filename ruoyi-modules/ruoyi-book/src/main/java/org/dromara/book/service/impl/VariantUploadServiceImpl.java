package org.dromara.book.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.book.domain.entity.BizVariantUpload;
import org.dromara.book.domain.vo.VariantUploadVo;
import org.dromara.book.mapper.BizVariantUploadMapper;
import org.dromara.book.service.IVariantUploadService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 * 举一反三母题图上传 Service 实现。
 *
 * <p>链路：校验（图片类型白名单 + ≤10MB）→ {@link OssFactory#instance()} 默认 OSS
 * 配置上传（dev = sys_oss_config 'aliyun' 桶 ai-book，access_policy=1 公开读，
 * 匿名 GET 实测 200）→ INSERT biz_variant_upload 留痕 → 返回 {id, url}。
 *
 * <p>设计要点：
 * <ul>
 *   <li>🔴 URL 必须公网可读 —— 会被外部 LLM 中转（api.lk888.ai）抓取做多模态分析。
 *       默认 OSS 配置即公开读桶，故走 {@code OssFactory.instance()}（与
 *       SysOssServiceImpl.upload 同范式）；实际用的 configKey 记入留痕行
 *       oss_config_key，将来换桶/排查可追溯。若默认配置被切到私有桶，本端点
 *       产出的 URL 会 403 —— 届时改为 {@code OssFactory.instance("公开读configKey")} 显式指定。</li>
 *   <li>owner = {@code LoginHelper.getUserId()} 服务端强制（绝不信前端传的身份），
 *       同 QuestionServiceImpl.create。</li>
 *   <li>校验失败抛 {@link ServiceException} → GlobalExceptionHandler 出 R.fail →
 *       MisiktEnvelopeAdvice 转 {@code {code:500, message}}，FE 按 code!==1 判错。</li>
 *   <li>大小上限 10MB 与 application.yml {@code spring.servlet.multipart.max-file-size: 10MB}
 *       对齐（超限 Spring 先拒，这里是显式双保险 + 人话报错）。</li>
 * </ul>
 *
 * @author backend-dev
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariantUploadServiceImpl implements IVariantUploadService {

    /** 业务场景固定值（biz_scene 列默认值一致，显式写入防 DDL 默认值漂移） */
    private static final String BIZ_SCENE_VARIANT_MOTHER = "variant-mother";

    /** 大小上限 10MB（与 spring.servlet.multipart.max-file-size 对齐） */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 允许的图片 MIME 类型 → 兜底后缀（原始文件名无后缀时按 MIME 补） */
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "image/gif", ".gif"
    );

    private final BizVariantUploadMapper bizVariantUploadMapper;

    @Override
    public VariantUploadVo uploadImage(MultipartFile file) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("未登录不能上传");
        }
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.containsKey(contentType.toLowerCase())) {
            throw new ServiceException("仅支持 jpg/png/webp/gif 图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("图片不能超过 10MB");
        }

        // 后缀：优先取原始文件名的，没有则按 MIME 兜底（OssClient 按后缀拼对象键）
        String originalName = StringUtils.defaultString(file.getOriginalFilename());
        String suffix = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf('.'))
            : ALLOWED_CONTENT_TYPES.get(contentType.toLowerCase());

        // 默认 OSS 配置上传（dev = 'aliyun' 公开读桶，见类注释；与 SysOssServiceImpl.upload 同范式）
        OssClient storage = OssFactory.instance();
        UploadResult uploadResult;
        try {
            uploadResult = storage.uploadSuffix(file.getBytes(), suffix, contentType);
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败: " + e.getMessage());
        }

        // 留痕落库（owner 服务端强制）
        Date now = new Date();
        BizVariantUpload row = new BizVariantUpload();
        row.setOssUrl(uploadResult.getUrl());
        row.setObjectKey(uploadResult.getFilename());
        row.setOriginalName(StringUtils.substring(originalName, 0, 255));
        row.setFileSize(file.getSize());
        row.setContentType(contentType);
        row.setBizScene(BIZ_SCENE_VARIANT_MOTHER);
        row.setOssConfigKey(storage.getConfigKey());
        row.setCreateUser(currentUserId);
        row.setCreateBy(String.valueOf(currentUserId));
        row.setCreateTime(now);
        row.setUpdateBy(String.valueOf(currentUserId));
        row.setUpdateTime(now);
        bizVariantUploadMapper.insert(row);

        log.info("[variant-upload] user={} id={} key={} size={} url={}",
            currentUserId, row.getId(), uploadResult.getFilename(), file.getSize(), uploadResult.getUrl());
        return new VariantUploadVo(row.getId(), uploadResult.getUrl());
    }
}
