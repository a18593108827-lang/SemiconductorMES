package com.mes.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 登录校验 + 强制改密 API 兜底
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final MustChangePwdInterceptor mustChangePwdInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/ws", "/ws/**")
                .order(0);

        registry.addInterceptor(mustChangePwdInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/ws", "/ws/**")
                .order(1);
    }
}
