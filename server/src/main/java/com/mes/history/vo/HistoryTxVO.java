package com.mes.history.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 履历一行：表里的事实 + 查的时候填的站名/机台/配方/严重级别。UI 不要自己 parse JSON。 */
@Data
public class HistoryTxVO {
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
    private String eqpCode;
    private String eqpName;
    private Long recipeId;
    private Long recipeVersionId;
    private Integer recipeVersionNo;
    private Long routeVersionId;
    private String remark;
    private String extJson;
    /** 解析后的扩展对象（reasonCode / qty / moveKind…）；坏 JSON 就空着，行还在 */
    private Object ext;
    /** danger=HOLD/SCRAP；warning=ABORT/REWORK/SKIP/OFF_FLOW/BONUS；其余 info（未知码也 info，禁止丢行） */
    private String severity;
    private Long operUserId;
    private String operUserName;
    private LocalDateTime createTime;
}
