package org.dromara.book.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 导出记录详情 / 列表项 VO（GET /teacher/export/record/{id} + .../page，PRD-A-014）。
 *
 * <p>契约：{@code {recordId, status, progress, fileName, fileUrl, fileSize, errorMsg, expireAt, createTime}}
 * <ul>
 *   <li>{@code recordId} — Long 经全局 BigNumberSerializer 出 string。</li>
 *   <li>{@code status} — '0'排队 / '1'生成中 / '2'完成 / '3'失败（CHAR(1) 原样 string）。</li>
 *   <li>过期态由 FE 判：status='2' 且 expireAt &lt; now → 显示"已过期"（BE 不改 status，惰性）。</li>
 * </ul>
 *
 * @author backend-dev
 */
@Data
@JsonPropertyOrder({
    "recordId", "status", "progress", "fileName", "fileUrl",
    "fileSize", "errorMsg", "expireAt", "createTime"
})
public class ExportRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记录 id（Long → string） */
    private Long recordId;

    /** 状态：'0'排队 '1'生成中 '2'完成 '3'失败 */
    private String status;

    /** 进度 0-100 */
    private Integer progress;

    /** 导出文件名 */
    private String fileName;

    /** OSS 公网 PDF URL（status=2 时非空） */
    private String fileUrl;

    /** PDF 字节数 */
    private Long fileSize;

    /** 失败原因（status=3 时） */
    private String errorMsg;

    /** OSS 留存到期（FE 判过期用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireAt;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 由 entity 转 VO。
     */
    public static ExportRecordVo of(org.dromara.book.domain.entity.BizExportRecord e) {
        ExportRecordVo vo = new ExportRecordVo();
        vo.setRecordId(e.getId());
        vo.setStatus(e.getStatus());
        vo.setProgress(e.getProgress());
        vo.setFileName(e.getFileName());
        vo.setFileUrl(e.getFileUrl());
        vo.setFileSize(e.getFileSize());
        vo.setErrorMsg(e.getErrorMsg());
        vo.setExpireAt(e.getExpireAt());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
