package com.mes.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 开启异步，用于审计日志落库
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
