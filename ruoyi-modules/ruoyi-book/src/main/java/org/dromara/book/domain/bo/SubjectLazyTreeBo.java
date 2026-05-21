package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /teacher/question/lazyTree 入参 BO。
 *
 * <p>misikt 抓包真实行为：入参 parentId 在 root 调用时传 "0"，BE 一次性返整树（不真懒）。
 * V0.1 简化：不论 parentId 传什么，全量返根 + 整树。
 *
 * @author backend-dev
 */
@Data
public class SubjectLazyTreeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 父节点 ID（V0.1 忽略此入参，永远返整树）
     */
    private String parentId;
}
