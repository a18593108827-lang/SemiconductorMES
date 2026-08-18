package com.mes.config;

import cn.dev33.satoken.stp.StpUtil;
import com.mes.common.BusinessException;
import com.mes.common.ResultCode;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * must_change_pwd=1 时仅允许 info / 改密 / 登出
 */
@Component
@RequiredArgsConstructor
public class MustChangePwdInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED = Set.of(
            "GET /auth/info",
            "PUT /auth/password",
            "POST /auth/logout"
    );

    private final SysUserMapper sysUserMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!StpUtil.isLogin()) {
            return true;
        }
        String key = request.getMethod().toUpperCase() + " " + normalizePath(request);
        if (ALLOWED.contains(key)) {
            return true;
        }

        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getMustChangePwd() != null && user.getMustChangePwd() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先修改密码");
        }
        return true;
    }

    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
