package org.dromara.admincommon.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * image_asset 表的写 Mapper（admin 端通用 — 跨业务模块共用，本模块由 H1 卡补丁抽离）。
 *
 * <p>原物理位置 {@code org.dromara.bookadmin.mapper.AdminImageAssetWriteMapper}（H1 卡段② BE 波 2c）。
 * 抽离到 admin-common 模块后，H 卡（题库 admin）/ A 卡（AI 试卷生成 admin）/ B 卡（试卷管理 admin）
 * 等所有 admin 业务模块上传图都走本 Mapper，不再重复造。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — image_asset 业务表无 tenant_id 列。
 *
 * <p>本 Mapper 不绑 BaseMapperPlus（无 entity 类）— mapper.xml 落
 * {@code ruoyi-admin-common/src/main/resources/mapper/admincommon/AdminImageAssetWriteMapper.xml}。
 *
 * @author backend-dev (H1 卡补丁 — admin-common 抽离)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminImageAssetWriteMapper {

    /**
     * 新增 image_asset 行（admin 直传 OSS 场景，跨业务模块通用）。
     *
     * <p>字段策略（image_asset 表语义对齐 — 原本是 CDN 镜像下载状态机）：
     * <ul>
     *   <li>{@code entity_type} 参数化 — 调用方决定（question / paper / ai-gen / ...）</li>
     *   <li>{@code entity_ref} 参数化 — 调用方决定来源（admin / paper-gen / ai-gen / ...）</li>
     *   <li>{@code asset_kind} 参数化 — 调用方决定（stem / answer / explain / cover / ...）</li>
     *   <li>{@code src_url} 形如 'admin-upload://&lt;oss-key&gt;'（NOT NULL 兜底）</li>
     *   <li>{@code url_hash} = SHA-256(src_url) hex 64 字符（NOT NULL UNIQUE，char(64)）</li>
     *   <li>{@code rel_path} = OSS key 完整路径（NOT NULL）</li>
     *   <li>{@code local_path=''}（NOT NULL 但 admin 直传无本地路径，给空串）</li>
     *   <li>{@code status='ok'} / {@code oss_url=...} / {@code oss_uploaded_ts=NOW()}</li>
     * </ul>
     *
     * <p>useGeneratedKeys: 取回自增 id 通过 keyProperty="idHolder.id" 回填到入参 Map。
     *
     * @param entityType  image_asset.entity_type（question / paper / ai-gen / ...）
     * @param assetKind   image_asset.asset_kind（stem / answer / explain / cover / ...）
     * @param entityRef   image_asset.entity_ref（来源标识：admin / paper-gen / ai-gen / ...）
     * @param srcUrl      虚 URL（{@code admin-upload://<oss-key>}）
     * @param urlHash     SHA-256(srcUrl) hex
     * @param host        OSS host（可空）
     * @param ext         扩展名（不带点，如 png / jpg）
     * @param relPath     OSS key 完整路径
     * @param localPath   空串（admin 直传无本地路径）
     * @param fileSize    文件字节数
     * @param contentType MIME 类型
     * @param ossUrl      OSS 返回 URL
     * @param idHolder    单元素 map 用于回填自增 id（MyBatis useGeneratedKeys 配合 keyProperty）
     * @return 影响行数（1 = 写入成功）
     */
    int insertAdminUpload(@Param("entityType") String entityType,
                          @Param("assetKind") String assetKind,
                          @Param("entityRef") String entityRef,
                          @Param("srcUrl") String srcUrl,
                          @Param("urlHash") String urlHash,
                          @Param("host") String host,
                          @Param("ext") String ext,
                          @Param("relPath") String relPath,
                          @Param("localPath") String localPath,
                          @Param("fileSize") Long fileSize,
                          @Param("contentType") String contentType,
                          @Param("ossUrl") String ossUrl,
                          @Param("idHolder") java.util.Map<String, Object> idHolder);
}
