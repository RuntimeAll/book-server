package org.dromara.book.domain.vo;

import java.io.Serializable;

/**
 * 提交导出任务响应 VO（POST /teacher/export/paper，PRD-A-014）。
 *
 * <p>envelope 拆后 FE 拿 {@code {recordId, estimatedSeconds}}：
 * <ul>
 *   <li>{@code recordId} — biz_export_record 主键，Long 经全局 BigNumberSerializer 出 string。</li>
 *   <li>{@code estimatedSeconds} — 预计生成秒数（FE 赛跑窗口 + 转后台通知文案用）。</li>
 * </ul>
 *
 * @author backend-dev
 */
public class ExportSubmitVo implements Serializable {

    private Long recordId;
    private Integer estimatedSeconds;

    public ExportSubmitVo() {
    }

    public ExportSubmitVo(Long recordId, Integer estimatedSeconds) {
        this.recordId = recordId;
        this.estimatedSeconds = estimatedSeconds;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getEstimatedSeconds() {
        return estimatedSeconds;
    }

    public void setEstimatedSeconds(Integer estimatedSeconds) {
        this.estimatedSeconds = estimatedSeconds;
    }
}
