package com.mes.hold.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 预约锁批出参 */
@Data
public class MesFutureHoldVO {
    private Long id;
    private Long lotId;
    private String lotNo;
    private Long routeVersionId;
    private Integer targetSortNo;
    /** PRE / POST */
    private String timing;
    private Long reasonId;
    private String reasonCode;
    private String reasonName;
    /** pending / activated / cancelled */
    private String status;
    /** 激活后关联的 mes_hold.id */
    private Long holdId;
    private String remark;
    private Long ownerUserId;
    private String ownerUserName;
    private Long createUserId;
    private String createUserName;
    private LocalDateTime createTime;
    private LocalDateTime activateTime;
    private Long cancelUserId;
    private String cancelUserName;
    private LocalDateTime cancelTime;
    private String cancelRemark;
}
