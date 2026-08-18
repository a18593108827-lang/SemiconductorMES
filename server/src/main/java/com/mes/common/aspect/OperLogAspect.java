package com.mes.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mes.common.annotation.OperLog;
import com.mes.system.entity.SysOperLog;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作审计切面：拦截 {@link OperLog} 并异步入库
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final SysOperLogService sysOperLogService;
    private final SysUserMapper sysUserMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLog entity = new SysOperLog();
        entity.setModule(operLog.module());
        entity.setAction(operLog.action());
        entity.setCreateTime(LocalDateTime.now());
        fillRequest(entity, joinPoint);
        fillUser(entity);

        try {
            Object result = joinPoint.proceed();
            entity.setStatus(1);
            if (entity.getUserId() == null) {
                fillUser(entity);
            }
            entity.setCostTime(System.currentTimeMillis() - start);
            sysOperLogService.saveAsync(entity);
            return result;
        } catch (Throwable e) {
            entity.setStatus(0);
            entity.setErrorMsg(StrUtil.sub(e.getMessage(), 0, 500));
            if (entity.getUserId() == null) {
                fillUser(entity);
            }
            entity.setCostTime(System.currentTimeMillis() - start);
            sysOperLogService.saveAsync(entity);
            throw e;
        }
    }

    private void fillRequest(SysOperLog entity, ProceedingJoinPoint joinPoint) {
        // 获取方法请求信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            entity.setRequestUri(request.getRequestURI());
            entity.setRequestMethod(request.getMethod());
            entity.setIp(request.getRemoteAddr());
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> params = new LinkedHashMap<>();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                Object arg = args[i];
                if (arg == null || arg instanceof HttpServletRequest) {
                    continue;
                }
                if ("password".equalsIgnoreCase(names[i])) {
                    params.put(names[i], "******");
                    continue;
                }
                Object value = maskSensitive(arg);
                params.put(names[i], value);
                if (entity.getLotNo() == null) {
                    String lotNo = extractLotNo(names[i], value);
                    if (StrUtil.isNotBlank(lotNo)) {
                        entity.setLotNo(lotNo);
                    }
                }
            }
        }
        if (!params.isEmpty()) {
            entity.setRequestParam(StrUtil.sub(JSONUtil.toJsonStr(params), 0, 2000));
        }
    }

    private Object maskSensitive(Object arg) {
        try {
            String json = JSONUtil.toJsonStr(arg);
            if (json.contains("password") || json.contains("Password")) {
                var obj = JSONUtil.parseObj(json);
                for (String key : obj.keySet()) {
                    if (key.toLowerCase().contains("password")) {
                        obj.set(key, "******");
                    }
                }
                return obj;
            }
            return JSONUtil.parse(json);
        } catch (Exception e) {
            return String.valueOf(arg);
        }
    }

    private String extractLotNo(String name, Object value) {
        if (name == null || value == null) {
            return null;
        }
        if ("lotNo".equalsIgnoreCase(name) || "lotId".equalsIgnoreCase(name)) {
            return String.valueOf(value);
        }
        try {
            var obj = JSONUtil.parseObj(JSONUtil.toJsonStr(value));
            if (obj.containsKey("lotNo")) {
                return obj.getStr("lotNo");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void fillUser(SysOperLog entity) {
        try {
            if (!StpUtil.isLogin()) {
                return;
            }
            long userId = StpUtil.getLoginIdAsLong();
            entity.setUserId(userId);
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null) {
                entity.setUsername(user.getUserCode());
            }
        } catch (Exception e) {
            log.debug("填充操作人信息失败: {}", e.getMessage());
        }
    }
}
