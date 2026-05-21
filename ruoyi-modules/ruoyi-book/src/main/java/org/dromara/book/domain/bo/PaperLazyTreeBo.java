package org.dromara.book.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * /teacher/exam/paper/lazyTree 入参 BO（D 卡卷库视觉级还原）。
 *
 * <p>misikt 真响应抓包：FE 默认传 {@code {"type":2,"version":1010}}。
 * <ul>
 *   <li>{@code type=2}：固定值，表示"卷库目录树"（V0.5 BE 忽略）</li>
 *   <li>{@code version=1010}：教材版本号（V0.5 BE 忽略，我们仅有"浙教新版"）</li>
 * </ul>
 *
 * <p>所以 D 卡阶段两个字段都是入参占位 — 不参与查询条件。
 *
 * @author backend-dev
 */
@Data
public class PaperLazyTreeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 树类型，misikt 真响应固定 2 = 卷库目录树（V0.5 BE 忽略） */
    private Integer type;

    /** 教材版本号，misikt 真响应固定 1010 = 浙教新版（V0.5 BE 忽略） */
    private Integer version;
}
