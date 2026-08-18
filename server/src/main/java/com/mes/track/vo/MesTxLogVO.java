package com.mes.track.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 事务履历行 */
@Data
public class MesTxLogVO {
    private Long id;
    private Long lotId;
    private String lotNo;
    private String txType;
    private String fromStatus;
    private String toStatus;
    private Integer fromSortNo;
    private Integer toSortNo;
    private Long stepId;
    private String stepName;
    private Long eqpId;
    private Long recipeId;
    private Long recipeVersionId;
    private Long routeVersionId;
    private String remark;
    private String extJson;
    private Long operUserId;
    private String operUserName;
    private LocalDateTime createTime;
}
