package com.mes.alarm.service;

import java.util.Map;

/** 告警门面（Queue Time 等触发）；完整 Alarm 模块后置 */
public interface AlarmService {

    void raise(String code, String message, Map<String, Object> payload);
}
