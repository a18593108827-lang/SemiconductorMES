package com.mes.test.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 阈值规则 */
@Data
public class TestAdviceRuleVO {

    private Long id;

    /** 适用产品，不适用为空串 */
    private String productCode;

    /** 适用测试程序，不适用为空串 */
    private String programName;

    /** 适用程序版本，不适用为空串 */
    private String programVersion;

    /** HARD */
    private String binType;

    /** Bin 号 */
    private String binCode;

    /** 占比上限 0~1 */
    private BigDecimal maxRatio;

    /** SUGGEST_HOLD / SUGGEST_REWORK / SUGGEST_RETEST */
    private String suggestedAction;

    /** 确认 HOLD 预填原因码 */
    private String defaultReasonCode;

    /** 1 启用 / 0 停用 */
    private Integer enabled;

    /** 乐观锁版本 */
    private Integer version;

    /** 备注 */
    private String remark;
}
