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
 * 试卷导出任务记录实体（biz_export_record，V20，PRD-A-014）。
 *
 * <p>一行 = 一次后端异步导出任务：谁（user_id，服务端 {@code LoginHelper.getUserId()}
 * 强制，绝不信前端）/ 导哪卷（paperId，可空）/ 导出配置（options JSON：variant + watermark
 * + ids）/ 状态机（status：0 排队 → 1 生成中 → 2 完成 / 3 失败）/ 进度（progress 0-100）/
 * 耗时（durationMs，喂滚动预估模型）/ 产物（fileUrl + fileSize）/ 失败原因（errorMsg）/
 * OSS 留存到期（expireAt = now+7 天，惰性判过期，不依赖 snail-job）。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}
 * （类级 {@code @InterceptorIgnore(tenantLine="true")}）+ application.yml tenant.excludes
 * 已加 biz_export_record（双保险，V904/biz_variant_upload 同款）。
 *
 * <p>🔴 雪花 id 出 VO 必 string —— Long 字段经全局 Jackson BigNumberSerializer
 * （JacksonConfig：Long 超 MAX_SAFE_INTEGER 转 string）自动序列化为 string，VO 直接用 Long 即可。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_export_record")
public class BizExportRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：排队中 */
    public static final String STATUS_QUEUED = "0";
    /** 状态：生成中 */
    public static final String STATUS_RUNNING = "1";
    /** 状态：完成 */
    public static final String STATUS_DONE = "2";
    /** 状态：失败 */
    public static final String STATUS_FAILED = "3";

    /**
     * 主键（雪花 id，IdType.ASSIGN_ID — biz_export_record 无 AUTO_INCREMENT，靠 MyBatis-Plus 雪花填）
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 导出发起者 sys_user.id（服务端 LoginHelper 强制，硬隔离不信前端）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 来源试卷 biz_paper.id（可空：篮子直接导出无卷）
     */
    @TableField("paper_id")
    private Long paperId;

    /**
     * 导出文件名（弹窗里填的，默认 = 卷名）
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 导出配置 JSON 原文：{@code {"variant":"...","watermark":true,"ids":["..."]}}
     * （DB JSON 列，Java 侧存原始字符串，service 用 Jackson 解析）
     */
    @TableField("options")
    private String options;

    /**
     * 状态：0 排队 / 1 生成中 / 2 完成 / 3 失败
     */
    @TableField("status")
    private String status;

    /**
     * 进度 0-100
     */
    @TableField("progress")
    private Integer progress;

    /**
     * 生成耗时（毫秒，喂滚动预估模型）
     */
    @TableField("duration_ms")
    private Integer durationMs;

    /**
     * OSS 公网可访 PDF URL
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * PDF 字节数
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 失败原因（status=3 时）
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * OSS 留存到期（now+7 天，惰性判过期）
     */
    @TableField("expire_at")
    private Date expireAt;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;
}
