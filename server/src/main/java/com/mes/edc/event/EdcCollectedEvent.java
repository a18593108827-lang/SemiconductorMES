package com.mes.edc.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 采集写进库之后喊的一声。
 * 里面没有 SPC 类型；没人听也不影响采集。监听方再用 collectionId 去拉点。
 */
@Getter
@AllArgsConstructor
public class EdcCollectedEvent {
    private final Long collectionId;
    private final Long lotId;
    private final String lotNo;
    private final Long stepId;
    /** 站编码，拆服务时少查一次主数据 */
    private final String stepCode;
    private final Long eqpId;
    /** 机台编码，没有机台就是空 */
    private final String eqpCode;
    private final LocalDateTime collectedAt;
}
