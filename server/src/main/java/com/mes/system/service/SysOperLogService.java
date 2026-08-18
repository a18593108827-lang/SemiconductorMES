package com.mes.system.service;

import com.mes.system.entity.SysOperLog;

/**
 * 操作审计日志服务
 */
public interface SysOperLogService {

    /** 异步保存审计日志 */
    void saveAsync(SysOperLog log);
}
