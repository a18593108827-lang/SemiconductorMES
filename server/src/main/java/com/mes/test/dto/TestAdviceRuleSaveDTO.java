package com.mes.test.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 新增或修改阈值规则 */
@Data
public class TestAdviceRuleSaveDTO {

    /** 产品编码，不适用填空串 */
    private String productCode;

    /** 适用测试程序，不适用填空串 */
    private String programName;

    /** 适用程序版本，不适用填空串 */
    private String programVersion;

    /** P0 仅 HARD */
    private String binType;

    /** 规则盯的 Bin 号 */
    private String binCode;

    /** 占比上限 0~1 */
    private BigDecimal maxRatio;

    /** SUGGEST_HOLD / SUGGEST_REWORK / SUGGEST_RETEST */
    private String suggestedAction;

    /** 确认 HOLD 预填原因码，可空 */
    private String defaultReasonCode;

    /** 1 启用 / 0 停用，空则 1 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    /** 修改时必填，新增忽略 */
    private Integer version;
}
