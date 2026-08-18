package com.mes.dispatch.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 设备预约 */
@Data
public class DispatchReserveVO {
    private Long id;
    private Long lotId;
    private String lotNo;
    private Long eqpId;
    private String eqpCode;
    private String eqpName;
    /** active / released / expired / consumed */
    private String status;
    private LocalDateTime expireTime;
    private Long reserveUserId;
    private Long consumeTxId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
