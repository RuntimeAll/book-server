package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 图资产去重登记实体（image_asset）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>录入流水线把外部图（题干/答案/配图）传 OSS 后落本表登记，按 {@code url_hash}
 * （= sha256(文件字节)，唯一键）去重：命中已存在 hash 不重传、直接复用旧 assetId+ossUrl。
 *
 * <p>表已由 B0 patch 建好，本类只做 Java 映射，不建表。无 tenant_id 列 →
 * Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}（类级关多租户拦截器）。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("image_asset")
public class ImageAsset implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 来源 URL（原图地址 / 本地路径标识，NOT NULL） */
    private String srcUrl;

    /** sha256(文件字节) 去重唯一键（char(64)，NOT NULL UNIQUE） */
    private String urlHash;

    /** 来源 host（外链域名） */
    private String host;

    /** 资产种类：stem/answer/figure（NOT NULL） */
    private String assetKind;

    /** 文件后缀（不含点） */
    private String ext;

    /** 相对路径（NOT NULL，本接口存对象键/标识） */
    private String relPath;

    /** 本地路径（NOT NULL，localPath 直传时存原磁盘路径，否则存 OSS 对象键兜底） */
    private String localPath;

    /** 归属实体类型（question 等，可空） */
    private String entityType;

    /** 归属实体 id（可空） */
    private Long entityId;

    /** 归属实体引用（可空） */
    private String entityRef;

    /** 状态：uploaded 等（NOT NULL） */
    private String status;

    /** 文件字节数 */
    private Long fileSize;

    /** MIME 类型 */
    private String contentType;

    /** 下载 HTTP 码（外链抓取场景，本接口不填） */
    private Integer httpCode;

    /** 尝试次数 */
    private Integer attemptCount;

    /** 错误信息 */
    private String errMsg;

    /** 下载时间 */
    private Date downloadTs;

    /** OSS 地址（上传成功后回填） */
    private String ossUrl;

    /** OSS 上传时间 */
    private Date ossUploadedTs;

    /** 创建时间（DDL DEFAULT CURRENT_TIMESTAMP） */
    private Date createdAt;
}
