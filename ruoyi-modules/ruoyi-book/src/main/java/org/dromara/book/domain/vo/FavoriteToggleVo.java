package org.dromara.book.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * POST /teacher/qd/favorite/{id} toggle 响应 VO。
 *
 * <p>契约（PRD §3.1 B-2）：
 * <pre>
 *   {"isFavorite": true}    -- toggle 之后已收藏
 *   {"isFavorite": false}   -- toggle 之后已取消
 * </pre>
 *
 * @author backend-dev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteToggleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * toggle 之后的收藏状态
     */
    private Boolean isFavorite;
}
