package com.mes.route.vo;

import lombok.Data;

@Data
public class MesRouteEdgeVO {
    private Long id;
    private Integer fromSortNo;
    private Integer toSortNo;
    private String edgeType;
    private Integer maxReworkCount;
    private String reasonCodes;
    private String conditionCode;
    private Integer maxQueueMin;
    private Integer minQueueMin;
    private String onViolate;
    private Integer sortNo;
}
