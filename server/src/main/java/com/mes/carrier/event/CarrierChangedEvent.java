package com.mes.carrier.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 绑/解成功后发出；监听方 AFTER_COMMIT 消费，一期可无监听 */
@Getter
@AllArgsConstructor
public class CarrierChangedEvent {

    public static final String ACTION_BIND = "BIND";
    public static final String ACTION_UNBIND = "UNBIND";

    private final String action;
    private final Long lotId;
    private final String lotNo;
    private final Long carrierId;
    private final String carrierCode;
}
