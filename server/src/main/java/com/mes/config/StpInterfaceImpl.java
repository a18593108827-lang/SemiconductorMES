package com.mes.config;

import cn.dev33.satoken.stp.StpInterface;
import com.mes.system.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限/角色加载
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysPermissionMapper sysPermissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return sysPermissionMapper.selectPermCodesByUserId(Long.valueOf(loginId.toString()));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return sysPermissionMapper.selectRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
