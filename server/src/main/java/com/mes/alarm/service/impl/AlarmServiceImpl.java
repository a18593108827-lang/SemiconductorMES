package com.mes.alarm.service.impl;

import com.mes.alarm.service.AlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AlarmServiceImpl implements AlarmService {

    @Override
    public void raise(String code, String message, Map<String, Object> payload) {
        log.warn("[ALARM] code={} message={} payload={}", code, message, payload);
    }
}
