package com.mes.edc.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 采集头对外展示 */
@Data
public class MesEdcCollectionVO {
    private Long id;
    private Long lotId;
    private String lotNo;
    private Long routeVersionId;
    private Integer sortNo;
    private Long stepId;
    private String stepCode;
    private String stepName;
    private Long trackInTxId;
    private Long planId;
    /** PASS / FAIL */
    private String result;
    private String source;
    private Long eqpId;
    private String remark;
    private Long collectedBy;
    private LocalDateTime collectedAt;
    private Integer itemCount;
    private List<MesEdcCollectionItemVO> items = new ArrayList<>();
    private LocalDateTime createTime;
}
