package com.mes.hold.dto;

import lombok.Data;

/** 取消预约锁批入参 */
@Data
public class MesFutureHoldCancelDTO {
    /** 取消备注，可选 */
    private String remark;
}
