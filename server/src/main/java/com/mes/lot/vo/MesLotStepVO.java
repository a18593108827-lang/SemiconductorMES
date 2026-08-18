package com.mes.lot.vo;

import lombok.Data;

/** 放行快照下的有序步骤摘要（只读） */
@Data
public class MesLotStepVO {
    private Long stepId;
    private String stepCode;
    private String stepName;
    /** 顺序号 */
    private Integer sortNo;
    /** 下一站顺序号，空表示结束 */
    private Integer nextSortNo;
    private String eqpType;
    private Integer stepType;
}
