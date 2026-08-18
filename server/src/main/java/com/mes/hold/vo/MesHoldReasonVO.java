package com.mes.hold.vo;

import lombok.Data;

/** 锁批原因码 */
@Data
public class MesHoldReasonVO {
    private Long id;
    private String reasonCode;
    private String reasonName;
    private String category;
    private Integer status;
    private String remark;
}
