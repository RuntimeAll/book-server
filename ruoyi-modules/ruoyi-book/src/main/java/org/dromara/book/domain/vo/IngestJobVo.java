package org.dromara.book.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 录题作业列表行 VO（GET /teacher/ingest/jobs）—— PRD-A-002 路B。
 *
 * <p>供多功能球「进行中」轮询。雪花 id 用 Long，经全局 BigNumberSerializer 自动转 string。
 *
 * @author backend-dev (PRD-A-002 路B)
 */
@Data
public class IngestJobVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String status;
    private Integer questionCount;
    private Integer committedCount;
    private String sourceFileName;
    private String errorMsg;
    private Date createTime;
}
