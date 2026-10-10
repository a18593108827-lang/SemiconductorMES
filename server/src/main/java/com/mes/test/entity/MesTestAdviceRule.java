package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 处置建议阈值规则。不适用维度填空串 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_test_advice_rule")
public class MesTestAdviceRule extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 适用产品，不适用填空串 */
    private String productCode;

    /** 适用测试程序，不适用填空串 */
    private String programName;

    /** 适用程序版本，不适用填空串 */
    private String programVersion;

    /** P0 仅 HARD */
    private String binType;

    /** 规则盯的 Bin 号 */
    private String binCode;

    /** 占比上限 0~1，判定 ratio > maxRatio */
    private BigDecimal maxRatio;

    /** SUGGEST_HOLD / SUGGEST_REWORK / SUGGEST_RETEST */
    private String suggestedAction;

    /** 确认 HOLD 预填原因码，取自 Hold 字典 */
    private String defaultReasonCode;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 乐观锁 */
    @Version
    private Integer version;
}
