package org.dromara.scheduling.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * biz_ts_base · 老师全局基点（主线 C / MVP / user_id unique）
 */
@Data
@TableName("biz_ts_base")
public class BizTsBase implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String addressText;

    private BigDecimal lng;

    private BigDecimal lat;

    private String formattedAddress;

    private String addressLevel;

    private Long createBy;

    private Date createTime;

    private Date updateTime;
}
