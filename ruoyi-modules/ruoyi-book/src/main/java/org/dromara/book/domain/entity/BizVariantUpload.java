package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 举一反三母题图上传留痕实体（biz_variant_upload，V904）。
 *
 * <p>一行 = 一次"本地选图 → OSS 上传"留痕：谁（create_user，服务端
 * {@code LoginHelper.getUserId()} 强制）、何时、传了什么（original_name /
 * file_size / content_type）、落在哪（oss_url / object_key / oss_config_key）。
 *
 * <p>oss_url 必须公网可读 —— 会被外部 LLM 中转（api.lk888.ai）抓取做多模态分析；
 * 上传走默认 OSS 配置（dev = sys_oss_config 'aliyun'，access_policy=1 公开读）。
 *
 * <p>审计列照 biz_question 风格（create_by 若依用户名 + create_user sys_user.id），
 * 服务端显式 set（同 QuestionServiceImpl.create），不走 MetaObjectHandler 自动填。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_variant_upload")
public class BizVariantUpload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id（照项目 biz_* 表 AUTO_INCREMENT 风格）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * OSS 公网可读 URL（给 FE 回显 + LLM 中转抓取）
     */
    @TableField("oss_url")
    private String ossUrl;

    /**
     * OSS 对象键（桶内路径，删除/审计用）
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 上传时的原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件字节数
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * MIME 类型（image/png 等）
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 业务场景，现仅 'variant-mother'（举一反三母题图）
     */
    @TableField("biz_scene")
    private String bizScene;

    /**
     * 实际使用的 sys_oss_config.config_key（留痕，换桶可追溯）
     */
    @TableField("oss_config_key")
    private String ossConfigKey;

    /**
     * 若依用户名（服务端强制）
     */
    @TableField("create_by")
    private String createBy;

    /**
     * 上传者 sys_user.id（服务端 LoginHelper 强制，绝不信前端）
     */
    @TableField("create_user")
    private Long createUser;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_by")
    private String updateBy;

    @TableField("update_time")
    private Date updateTime;

    @TableField("remark")
    private String remark;
}
