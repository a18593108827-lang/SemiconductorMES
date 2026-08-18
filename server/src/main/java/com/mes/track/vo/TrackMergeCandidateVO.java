package com.mes.track.vo;

import lombok.Data;

/**
 * 可合入指定主批的候选摘要。
 * 已过滤：同产品 / 同快照 / 同站 / wait / 非 Hold / 非 Off-Flow / qty≥1。
 */
@Data
public class TrackMergeCandidateVO {
    private Long lotId;
    private String lotNo;
    private Integer qty;
    private String productCode;
    private Long routeVersionId;
    /** 当前站序，须与主批一致 */
    private Integer currentSortNo;
    private Long currentStepId;
    private String status;
}
