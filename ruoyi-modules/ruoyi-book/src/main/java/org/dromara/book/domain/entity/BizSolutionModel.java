package org.dromara.book.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 解题模型词库实体（biz_solution_model，模型维池）。
 *
 * <p>一行 = 一个解题模型（M01.. ），记触发特征（什么时候想到它）+ 动作→结论
 * （辅助线 + 得到什么），供举一反三闸B 评级与模型库匹配。
 *
 * <p>主键 id 是外部指定的字符串编号（"M01"..），非自增 → {@link IdType#INPUT}。
 *
 * <p>PRD-C-102 批0 新增 difficultyTier / freqBand 两列（模型化难度评级 + 双旋钮变式）。
 * 本批仅同步 entity，service/mapper/controller 由后续批次补。
 *
 * @author backend-dev
 */
@Data
@TableName("biz_solution_model")
public class BizSolutionModel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模型 id（外部指定字符串编号 M01..，非自增）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 模型名
     */
    @TableField("name")
    private String name;

    /**
     * 分组
     */
    @TableField("category")
    private String category;

    /**
     * 触发特征（什么时候想到它）
     */
    @TableField("trigger_feature")
    private String triggerFeature;

    /**
     * 动作→结论（辅助线 + 得到什么）
     */
    @TableField("action_conclusion")
    private String actionConclusion;

    /**
     * 是否路由条目（调度卡）：'1'=是
     */
    @TableField("is_router")
    private String isRouter;

    /**
     * 是否可附 GeoGebra 构造模板：'1'=可
     */
    @TableField("has_geo_template")
    private String hasGeoTemplate;

    /**
     * 排序
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 状态：'0'正常 '1'停用
     */
    @TableField("status")
    private String status;

    /**
     * 难度阶（PRD-C-102）：1=基础阶 2=高阶(预留3细分)
     */
    @TableField("difficulty_tier")
    private Integer difficultyTier;

    /**
     * 考频/通用度（派生,定期重算，PRD-C-102）：1=一次性低频 2=高频通用
     */
    @TableField("freq_band")
    private Integer freqBand;

    @TableField("remark")
    private String remark;

    @TableField("create_time")
    private Date createTime;
}
