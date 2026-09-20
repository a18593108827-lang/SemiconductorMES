package com.mes.report.vo;

import lombok.Data;

/** Move 报表：按站一点（Rep-2 填充；Rep-1 可为空列表） */
@Data
public class ReportStepPointVO {

    private Long stepId;
    private String stepCode;
    private String stepName;
    private long trackOutCount;
}
