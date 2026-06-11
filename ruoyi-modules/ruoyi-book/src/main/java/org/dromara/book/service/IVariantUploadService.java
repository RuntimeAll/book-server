package org.dromara.book.service;

import org.dromara.book.domain.vo.VariantUploadVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 举一反三母题图上传 Service。
 *
 * @author backend-dev
 */
public interface IVariantUploadService {

    /**
     * 上传母题图到 OSS（默认配置 = 公开读桶）并落 biz_variant_upload 留痕。
     *
     * @param file 图片文件（image/jpeg|png|webp|gif，≤10MB）
     * @return {id, url}，url 公网可读
     */
    VariantUploadVo uploadImage(MultipartFile file);
}
