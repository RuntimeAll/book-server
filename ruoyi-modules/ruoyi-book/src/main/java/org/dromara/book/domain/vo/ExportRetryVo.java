package org.dromara.book.domain.vo;

import java.io.Serializable;

/**
 * 重试导出响应 VO（POST /teacher/export/record/{id}/retry，PRD-A-014）。
 *
 * <p>envelope 拆后 FE 拿 {@code {recordId}}（新任务 id，Long → string）。
 *
 * @author backend-dev
 */
public class ExportRetryVo implements Serializable {

    private Long recordId;

    public ExportRetryVo() {
    }

    public ExportRetryVo(Long recordId) {
        this.recordId = recordId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }
}
