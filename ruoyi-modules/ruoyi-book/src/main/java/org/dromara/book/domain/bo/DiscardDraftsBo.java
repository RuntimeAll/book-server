package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * PRD-A-022 批0 — 批量软删草稿入参 BO（POST /teacher/question/discard-drafts）。
 *
 * <p>举一反三「换一批」时批量软删旧草稿。{@link #ids} 为待软删草稿题 id 列表（可空 / null →
 * service 直接返回 0，不报错）。service 端只软删本人（create_user=登录 id）且仍是草稿（status='0'）
 * 的题，绝不碰他人题、绝不碰已发布 '1'。
 *
 * @author backend-dev (PRD-A-022 批0)
 */
@Data
public class DiscardDraftsBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待批量软删的草稿题 id 列表（可空 / null → 返回 0 不报错） */
    private List<Long> ids;
}
