package com.mes.wip.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 在制列表行 */
@Data
public class MesWipVO {
    private Long lotId;
    private String lotNo;
    private String productCode;
    private Integer qty;
    private Integer priority;
    /** Hot Lot 0/1 */
    private Integer hotFlag;
    private String customerLot;
    private String status;
    private Integer currentSortNo;
    private Long currentStepId;
    private String currentStepCode;
    private String currentStepName;
    private Long currentEqpId;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private Long routeVersionId;
    private Integer routeVersionNo;
    private LocalDateTime updateTime;
}
