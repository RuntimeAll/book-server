package org.dromara.book.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.dromara.book.domain.vo.VariantUploadVo;
import org.dromara.book.service.IVariantUploadService;
import org.dromara.common.core.domain.R;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 举一反三 Controller（/teacher/variant/...）。
 *
 * <p>路径前缀 {@code /teacher/}，命中 {@link MisiktEnvelopeAdvice} 自动转 envelope。
 *
 * @author backend-dev
 */
@RestController
@RequestMapping("/teacher/variant")
@RequiredArgsConstructor
public class VariantController {

    private final IVariantUploadService variantUploadService;

    /**
     * POST /teacher/variant/upload-image — 举一反三母题图上传。
     *
     * <p>book-ui 举一反三页"本地选图"场景：multipart/form-data，字段名 {@code file}。
     * 校验 content-type ∈ image/(jpeg|png|webp|gif) + 大小 ≤10MB，不符抛
     * ServiceException → envelope {@code {code:500, message}}（FE 按 code!==1 判错）。
     *
     * <p>上传走默认 OSS 配置（公开读桶 —— URL 会被外部 LLM 中转抓取做多模态分析，
     * 必须公网可读），并落 biz_variant_upload 留痕（owner = 登录老师，服务端强制）。
     *
     * <p>响应（envelope 拆后）：{@code {id, url}}。
     */
    @SaCheckLogin
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<VariantUploadVo> uploadImage(@RequestPart("file") MultipartFile file) {
        return R.ok(variantUploadService.uploadImage(file));
    }
}
