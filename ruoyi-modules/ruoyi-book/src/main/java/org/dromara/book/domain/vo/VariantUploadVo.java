package org.dromara.book.domain.vo;

/**
 * 举一反三母题图上传响应 VO。
 *
 * <p>envelope 拆后 FE 拿到 {@code {id, url}}：
 * <ul>
 *   <li>{@code url} — OSS 公网可读 URL（FE 回显 + 透传给 AI 编排服务做多模态分析）</li>
 *   <li>{@code id}  — biz_variant_upload 留痕行 id（追溯/后续关联用）</li>
 * </ul>
 *
 * @param id  留痕行主键
 * @param url OSS 公网 URL
 * @author backend-dev
 */
public record VariantUploadVo(Long id, String url) {
}
