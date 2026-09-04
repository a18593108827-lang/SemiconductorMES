package com.mes.alarm.service;

import java.util.Map;

/**
 * 告警写入口：各域只 raise，不管确认/关闭。
 * 实现吞异常；失败不影响调用方事务。
 */
public interface AlarmService {

    /**
     * 记一条告警（落库、同键 OPEN 去重累加）。
     *
     * @param code    码表 code，如 SPC_OOC
     * @param message 给人看的说明
     * @param payload 上下文；常用 lotId / eqpId / chartId
     */
    void raise(String code, String message, Map<String, Object> payload);
}
