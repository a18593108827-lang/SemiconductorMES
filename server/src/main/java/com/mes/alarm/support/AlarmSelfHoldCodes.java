package com.mes.alarm.support;

import java.util.Set;

/**
 * 域内已自挂 Hold 的告警码：禁止再配 on_raise=HOLD_LOT，避免同一事件双通道锁批。
 * 新模块若「自挂 Hold + raise」，把 code 加进本集合。
 */
public final class AlarmSelfHoldCodes {

    public static final Set<String> CODES = Set.of(
            "PROCESS_TIME_VIOLATION",
            "QTIME_EXCEED"
    );

    private AlarmSelfHoldCodes() {
    }

    public static boolean isSelfHold(String alarmCode) {
        return alarmCode != null && CODES.contains(alarmCode.trim());
    }
}
