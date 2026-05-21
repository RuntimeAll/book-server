package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 试卷分类树节点实体（biz_paper_category）— D 卡卷库视觉级还原。
 *
 * <p>独立于 biz_subject 章节树，承担"卷库左侧目录"。4-15 位数字编码 PK，
 * misikt 真响应 lazyTree 共 97 节点（3 根：3001 公共试卷 / 3003 资料库 / 3004 专题卷库）。
 *
 * <p>seed 落库后 D 卡 V0.5 不再写入（先 read-only），未来 V1.5 后台管理才开 CRUD。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_paper_category")
public class BizPaperCategory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类 id（4-15 位数字编码字符串） */
    @TableId(value = "id")
    private String id;

    /**
     * 父节点 id（根节点 '0' / 6 行 deprecated 标记 '-deprecated'，service 层 WHERE parent_id NOT LIKE '-%'）
     */
    private String parentId;

    /** 节点名 */
    private String name;

    /** 同层排序（asc） */
    private Integer sort;

    /** 是否共享 0/1（DB TINYINT，misikt 真响应需归一化为 STRING '0'/'1'） */
    private Integer isShare;
}
