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
 * 批量录题作业实体（biz_ingest_job，V27，PRD-A-002 路B）。
 *
 * <p>一行 = 一次"上传文件 → 异步拆题"作业。状态机
 * {@code PENDING → EXTRACT_ING → SPLIT_ING → DONE / FAILED}，{@link org.dromara.book.service.IngestJobWorker}
 * 在专用线程池跑。归属 teacher_id 冗余（= create_user），job/item 查改删入库均比对当前登录（防越权 C5）。
 *
 * <p>🔴 无 tenant_id 列 → Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}
 * （类级 {@code @InterceptorIgnore(tenantLine="true")}）+ application.yml tenant.excludes 已加 biz_ingest_job。
 *
 * <p>🔴 雪花 id（ASSIGN_ID）；出 VO 用 Long 经全局 BigNumberSerializer 自动转 string。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Data
@TableName("biz_ingest_job")
public class BizIngestJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 状态：待处理（已建作业，未开跑） */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：抽取中（分流抽取文字/图） */
    public static final String STATUS_EXTRACT_ING = "EXTRACT_ING";
    /** 状态：拆题中（调 toolkit /split） */
    public static final String STATUS_SPLIT_ING = "SPLIT_ING";
    /** 状态：完成（拆题落 item，含 N=0 空态） */
    public static final String STATUS_DONE = "DONE";
    /** 状态：失败（error_msg 带因） */
    public static final String STATUS_FAILED = "FAILED";

    /** 作业ID（雪花） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属老师ID（= create_user 冗余，归属校验用，服务端 LoginHelper 强制） */
    @TableField("teacher_id")
    private Long teacherId;

    /** 绑定章节节点 biz_subject.id（整批粗挂） */
    @TableField("subject_id")
    private String subjectId;

    /** 上传文件名 */
    @TableField("source_file_name")
    private String sourceFileName;

    /** 上传文件/图 OSS 地址（原文件留存） */
    @TableField("source_oss_url")
    private String sourceOssUrl;

    /** 来源类型 image/pdf/docx/text */
    @TableField("source_type")
    private String sourceType;

    /** 分档 fast 快档(文字层)/slow 慢档(页图) */
    @TableField("lane")
    private String lane;

    /** 答案模式 from_source原卷自带/ai_solve AI解题/stem_only只录题 */
    @TableField("answer_mode")
    private String answerMode;

    /** 入库方式 review审核后/direct直接 */
    @TableField("commit_mode")
    private String commitMode;

    /** 学段提示（约束解法不超纲，可空） */
    @TableField("grade_hint")
    private String gradeHint;

    /** 状态 PENDING/EXTRACT_ING/SPLIT_ING/DONE/FAILED */
    @TableField("status")
    private String status;

    /** 失败原因（FAILED 时带因） */
    @TableField("error_msg")
    private String errorMsg;

    /** 拆出题数 */
    @TableField("question_count")
    private Integer questionCount;

    /** 已入库题数 */
    @TableField("committed_count")
    private Integer committedCount;

    /** 完整度过滤丢弃项（JSON 数组，可读原因） */
    @TableField("dropped_json")
    private String droppedJson;

    @TableField("create_dept")
    private Long createDept;

    @TableField("create_by")
    private String createBy;

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
