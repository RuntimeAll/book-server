package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 审核勾选入库入参（POST /teacher/ingest/job/{jobId}/commit）—— PRD-A-002 路B。
 *
 * <p>{@code itemIds} 为空/缺省 = 入库该作业全部 pending 项。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Data
public class IngestJobCommitBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 勾选要入库的 item id 列表（空/null = 全部 pending 项） */
    private List<Long> itemIds;
}
