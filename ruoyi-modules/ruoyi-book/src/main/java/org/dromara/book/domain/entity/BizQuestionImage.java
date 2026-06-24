package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目-图片关联实体（biz_question_image）—— PRD-C-204 B1 录入接口新建。
 *
 * <p>一道题挂 N 张图（题干图/答案图/配图），每张图引一条 image_asset（asset_id）+ 冗余 oss_url。
 * 录题事务内随题写入（{@code role}=stem/answer/figure，{@code block_id} 关联 blockJson 图块）。
 *
 * <p>表已由 B0 patch 建好，本类只做 Java 映射，不建表。无 tenant_id 列 →
 * Mapper 继承 {@link org.dromara.book.mapper.BizBaseMapper}（类级关多租户拦截器）。
 *
 * @author backend-dev (PRD-C-204 B1)
 */
@Data
@TableName("biz_question_image")
public class BizQuestionImage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（DDL BIGINT AUTO_INCREMENT） */
    @TableId(value = "id")
    private Long id;

    /** 题目 id（biz_question.id，NOT NULL） */
    private Long questionId;

    /** 图资产 id（image_asset.id，NOT NULL） */
    private Long assetId;

    /** OSS 地址（冗余，便于直接渲染） */
    private String ossUrl;

    /** 关联 blockJson 图块 id（可空） */
    private String blockId;

    /** 角色：stem/answer/figure（可空） */
    private String role;

    /** 同角色内顺序 */
    private Integer seq;

    /** 是否装饰性（不参与检索/判分，NOT NULL DEFAULT 0） */
    private Integer isDecorative;

    /** 创建时间 */
    private Date createTime;
}
