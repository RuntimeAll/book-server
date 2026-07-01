package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 举一反三变式血缘实体（biz_variation_trace，V27，运行期写）。
 *
 * <p>一行 = 一次「母题 → 变式」留痕：变式算子（operator）、与母题相似度
 * （similarity）、目标难度档 vs 闸B 算出的实际难度档（target/actual_level）、
 * 回炉次数（retries）。作举一反三可追溯链，由编排服务运行期写入。
 *
 * <p>tenant 隔离照 biz_* 惯例在 mapper 层处理（{@code BizBaseMapper} 类级
 * {@code @InterceptorIgnore(tenantLine="true")}，biz_* 表无 tenant_id 列）；
 * 本批仅同步 entity，mapper/service/controller 由后续批次补。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_variation_trace")
public class BizVariationTrace implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键自增 id（照项目 biz_* 表 AUTO_INCREMENT 风格）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 变式题 id
     */
    @TableField("variant_question_id")
    private Long variantQuestionId;

    /**
     * 母题 id
     */
    @TableField("mother_question_id")
    private Long motherQuestionId;

    /**
     * 所用变式算子（数值/结构/情境/条件增删/逆向/推广/升维/分类/定值化）
     */
    @TableField("operator")
    private String operator;

    /**
     * 与母题相似度 0-1
     */
    @TableField("similarity")
    private BigDecimal similarity;

    /**
     * 目标难度档 1-4
     */
    @TableField("target_level")
    private Integer targetLevel;

    /**
     * 闸B 算出的实际难度档 1-4
     */
    @TableField("actual_level")
    private Integer actualLevel;

    /**
     * 回炉次数
     */
    @TableField("retries")
    private Integer retries;

    /**
     * 入库时间
     */
    @TableField("create_time")
    private Date createTime;
}
