package org.dromara.bookadmin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * image_asset 表的写 Mapper（H1 卡段② BE 波 2c — V-4 fileUpload）。
 *
 * <p><strong>为什么不复用 ruoyi-book 的 mapper：</strong>
 * 用户 2026-05-22 拍板模块隔离铁则 — admin 改动禁碰 {@code ruoyi-book/} 模块。
 * 且 image_asset 在 ruoyi-book 模块根本没有 entity 也没有 mapper（之前是外部
 * 逆向工程进程产出的 CDN 镜像状态机表，业务代码侧从未碰过），所以本写 Mapper
 * 必须在 admin 模块独立新建。
 *
 * <p>🔴 必须 {@code @InterceptorIgnore(tenantLine = "true")} — image_asset 业务表
 * 无 tenant_id 列（与 B 卡 T7 教训一致）。
 *
 * <p>本 Mapper 不绑 BaseMapperPlus（无 entity 类）— mapper.xml 落
 * {@code ruoyi-book-admin/src/main/resources/mapper/bookadmin/AdminImageAssetWriteMapper.xml}。
 *
 * @author backend-dev (H1 卡段② BE 波 2c)
 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AdminImageAssetWriteMapper {

    /**
     * 新增 image_asset 行（admin 直传 OSS 场景）。
     *
     * <p>字段策略（image_asset 表语义对齐 — 原本是 CDN 镜像下载状态机）：
     * <ul>
     *   <li>{@code entity_type='question'} / {@code asset_kind} = stem/answer/explain</li>
     *   <li>{@code entity_ref='admin'}（标识来源 — 表无 source 字段，复用 entity_ref 占位）</li>
     *   <li>{@code src_url='admin-upload://<oss-key>'}（NOT NULL 兜底，标识 admin 直传）</li>
     *   <li>{@code url_hash} = SHA-256(src_url) 32B hex = 64 字符（NOT NULL UNIQUE，char(64)）</li>
     *   <li>{@code rel_path} = OSS key（NOT NULL，复用 OSS key 作 rel 标识）</li>
     *   <li>{@code local_path=''}（NOT NULL 但 admin 直传无本地路径，给空串）</li>
     *   <li>{@code status='ok'} / {@code oss_url=...} / {@code oss_uploaded_ts=NOW()}</li>
     * </ul>
     *
     * <p>useGeneratedKeys: 取回自增 id 通过 keyProperty="id" 回填到入参 Map / Bo
     * （这里入参是命名参数 + map 形式 → 取自增 id 走另一参数承载）。
     *
     * @param assetKind   stem / answer / explain
     * @param entityRef   来源标识（admin）
     * @param srcUrl      虚 URL（admin-upload://<oss-key>）
     * @param urlHash     SHA-256(srcUrl) hex
     * @param host        OSS host（可空）
     * @param ext         扩展名（不带点，如 png / jpg）
     * @param relPath     OSS key 完整路径
     * @param localPath   空串（admin 直传无本地路径）
     * @param fileSize    文件字节数
     * @param contentType MIME 类型
     * @param ossUrl      OSS 返回 URL
     * @param idHolder    单元素数组用于回填自增 id（MyBatis useGeneratedKeys 配合 keyProperty）
     * @return 影响行数（1 = 写入成功）
     */
    int insertAdminUpload(@Param("assetKind") String assetKind,
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
