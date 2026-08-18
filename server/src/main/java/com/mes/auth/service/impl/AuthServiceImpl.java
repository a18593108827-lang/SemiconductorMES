package com.mes.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.auth.dto.ChangePasswordDTO;
import com.mes.auth.dto.LoginDTO;
import com.mes.auth.dto.RegisterDTO;
import com.mes.auth.service.AuthService;
import com.mes.auth.vo.LoginVO;
import com.mes.auth.vo.UserInfoVO;
import com.mes.common.AssertUtil;
import com.mes.common.PasswordUtil;
import com.mes.system.entity.SysPermission;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysPermMapper;
import com.mes.system.mapper.SysPermissionMapper;
import com.mes.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysPermMapper sysPermMapper;

    @Override
    public void register(RegisterDTO dto) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserCode, dto.getUserCode()));
        AssertUtil.isTrue(count == 0, "用户编码已存在");

        SysUser user = new SysUser();
        user.setUserCode(dto.getUserCode());
        user.setUserName(dto.getUserName());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setStatus(1);
        user.setMustChangePwd(0);
        user.setSource("local");
        sysUserMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserCode, dto.getUserCode()));
        AssertUtil.notNull(user, "用户编码或密码错误");
        AssertUtil.isTrue(PasswordUtil.matches(dto.getPassword(), user.getPassword()), "用户编码或密码错误");
        AssertUtil.isTrue(user.getStatus() != null && user.getStatus() == 1, "账号已禁用");

        StpUtil.login(user.getId());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenName(StpUtil.getTokenName());
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserInfoVO getInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        AssertUtil.notNull(user, "用户不存在");

        List<String> roles = sysPermissionMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = sysPermissionMapper.selectPermCodesByUserId(userId);
        Set<String> permSet = new HashSet<>(permissions);

        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUserCode(user.getUserCode());
        vo.setUserName(user.getUserName());
        vo.setMustChangePwd(user.getMustChangePwd() == null ? 0 : user.getMustChangePwd());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        vo.setMenus(buildMenus(permSet));
        return vo;
    }

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        AssertUtil.notNull(user, "用户不存在");
        AssertUtil.isTrue(PasswordUtil.matches(dto.getOldPassword(), user.getPassword()), "当前密码错误");
        AssertUtil.isTrue(!dto.getOldPassword().equals(dto.getNewPassword()), "新密码不能与旧密码相同");
        AssertUtil.isTrue(dto.getNewPassword().length() >= 6, "新密码至少6位");

        user.setPassword(PasswordUtil.encode(dto.getNewPassword()));
        user.setMustChangePwd(0);
        sysUserMapper.updateById(user);
        StpUtil.logout();
    }

    private List<UserInfoVO.MenuVO> buildMenus(Set<String> permSet) {
        List<SysPermission> list = sysPermMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getStatus, 1)
                .in(SysPermission::getPermType, 1, 2)
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));

        Map<Long, UserInfoVO.MenuVO> map = new HashMap<>();
        for (SysPermission p : list) {
            UserInfoVO.MenuVO m = new UserInfoVO.MenuVO();
            m.setId(p.getId());
            m.setParentId(p.getParentId());
            m.setPermType(p.getPermType());
            m.setPermCode(p.getPermCode());
            m.setPermName(p.getPermName());
            m.setPath(p.getPath());
            m.setIcon(p.getIcon());
            m.setSortNo(p.getSortNo());
            map.put(p.getId(), m);
        }

        List<UserInfoVO.MenuVO> roots = new ArrayList<>();
        for (UserInfoVO.MenuVO m : map.values()) {
            Long parentId = m.getParentId() == null ? 0L : m.getParentId();
            if (parentId == 0L || !map.containsKey(parentId)) {
                roots.add(m);
            } else {
                map.get(parentId).getChildren().add(m);
            }
        }
        sortMenus(roots);
        return filterMenus(roots, permSet);
    }

    private List<UserInfoVO.MenuVO> filterMenus(List<UserInfoVO.MenuVO> nodes, Set<String> permSet) {
        List<UserInfoVO.MenuVO> result = new ArrayList<>();
        for (UserInfoVO.MenuVO node : nodes) {
            List<UserInfoVO.MenuVO> children = filterMenus(node.getChildren(), permSet);
            boolean selfVisible = false;
            if (node.getPermType() != null && node.getPermType() == 2) {
                selfVisible = StringUtils.hasText(node.getPermCode()) && permSet.contains(node.getPermCode());
            } else if (node.getPermType() != null && node.getPermType() == 1) {
                selfVisible = !children.isEmpty();
            }
            if (selfVisible) {
                node.setChildren(children);
                result.add(node);
            }
        }
        return result;
    }

    private void sortMenus(List<UserInfoVO.MenuVO> nodes) {
        nodes.sort(Comparator
                .comparing(UserInfoVO.MenuVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(UserInfoVO.MenuVO::getId));
        for (UserInfoVO.MenuVO n : nodes) {
            sortMenus(n.getChildren());
        }
    }
}
