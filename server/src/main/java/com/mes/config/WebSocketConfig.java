package com.mes.config;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket：端点 /ws，业务 topic 前缀 /topic。
 * 连接时校验 Sa-Token（Authorization: Bearer xxx，或头 token）。
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 前端连/ws这个地址；* 放开跨域，方便 Admin 本地调试 */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    /**
     * 消息怎么走：服务端往 /topic/** 推（客户端订阅）；
     * 客户端若往服务端发，前缀走 /app（本期告警只推不收，先占好）。
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 进站拦截：只有 CONNECT 时验登录。
     * 没 token / token 废了直接拒绝连上，别让匿名听告警。
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            /** CONNECT 帧里抠 token，校验通过后把登录人挂到会话上 */
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
                    return message;
                }
                String token = resolveToken(accessor);
                if (!StringUtils.hasText(token)) {
                    throw new IllegalArgumentException("WebSocket 未登录：缺少 token");
                }
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId == null) {
                    throw new IllegalArgumentException("WebSocket token 无效或已过期");
                }
                accessor.setUser(() -> String.valueOf(loginId));
                return message;
            }
        });
    }

    /**
     * 从 CONNECT 头取 token：先 Authorization: Bearer xxx，再原生头 token；
     * 都没有返回 null。
     */
    private static String resolveToken(StompHeaderAccessor accessor) {
        String auth = accessor.getFirstNativeHeader("Authorization");
        if (StringUtils.hasText(auth)) {
            String v = auth.trim();
            if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return v.substring(7).trim();
            }
            return v;
        }
        String token = accessor.getFirstNativeHeader("token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        return null;
    }
}
