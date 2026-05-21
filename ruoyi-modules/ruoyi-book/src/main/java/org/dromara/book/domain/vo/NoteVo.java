package org.dromara.book.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * GET /teacher/qd/note/{id} 响应 VO。
 *
 * <p>契约（PRD §3.1 B-4）：
 * <pre>
 *   null                                       -- 该题尚无笔记
 *   {"content":"xxx", "updateTime":"..."}      -- 已有笔记
 * </pre>
 *
 * @author backend-dev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 备注内容
     */
    private String content;

    /**
     * 最近修改时间
     */
    private Date updateTime;
}
