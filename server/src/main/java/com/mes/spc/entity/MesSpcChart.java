package com.mes.spc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPC 图：某站某特性（可选某机）的控制限。
 * 不存点值，点仍在 EDC。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_spc_chart")
public class MesSpcChart extends BaseEntity {

    /** 站级图不绑机台时库里存 0，唯一索引才立得住 */
    public static final long EQP_NONE = 0L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long paramId;
    private Long stepId;
    /** 0=该站全部机，不要存 null */
    private Long eqpId;
    /** 一期固定 IMR */
    private String chartType;
    /** MANUAL 手填限 / LEARNING 攒够点再算 */
    private String limitMode;
    /** 学习要攒够多少点，默认 25 */
    private Integer learningN;
    private BigDecimal ucl;
    private BigDecimal cl;
    private BigDecimal lcl;
    /** 连跑同侧点数；0=关掉这条规则，默认 7 */
    private Integer runN;
    /** 1启用 0停用；停了采集后不判 */
    private Integer enabled;

    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
