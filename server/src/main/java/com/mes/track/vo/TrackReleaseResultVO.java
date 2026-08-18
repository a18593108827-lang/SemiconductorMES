package com.mes.track.vo;

import lombok.Data;

/**
 * Track 放行结果
 */
@Data
public class TrackReleaseResultVO {
    private Long lotId;
    private String lotNo;
    private Long routeVersionId;
    private Integer routeVersionNo;
    /** 放行后状态，一期为 wait */
    private String status;
    private Integer currentSortNo;
    private Long currentStepId;
}
