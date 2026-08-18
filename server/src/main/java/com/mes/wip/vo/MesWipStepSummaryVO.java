package com.mes.wip.vo;

import lombok.Data;

/** 按站在制汇总 */
@Data
public class MesWipStepSummaryVO {
    private Integer sortNo;
    private Long stepId;
    private String stepCode;
    private String stepName;
    private Long waitCount;
    private Long processingCount;
    private Long heldCount;
    private Long total;
}
