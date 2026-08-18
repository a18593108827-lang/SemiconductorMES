package com.mes.system.service.impl;

import com.mes.system.entity.SysOperLog;
import com.mes.system.mapper.SysOperLogMapper;
import com.mes.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogServiceImpl implements SysOperLogService {

    private final SysOperLogMapper sysOperLogMapper;

    @Async
    @Override
    public void saveAsync(SysOperLog operLog) {
        try {
            sysOperLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("保存操作审计日志失败", e);
        }
    }
}
