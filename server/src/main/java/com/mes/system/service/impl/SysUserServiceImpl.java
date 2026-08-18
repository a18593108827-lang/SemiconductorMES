package com.mes.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.common.PasswordUtil;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.dto.SysUserResetPwdDTO;
import com.mes.system.dto.SysUserRoleAssignDTO;
import com.mes.system.dto.SysUserStatusDTO;
import com.mes.system.dto.SysUserUpdateDTO;
import com.mes.system.dto.UserRoleCodeRow;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysUser;
import com.mes.system.entity.SysUserRole;
import com.mes.system.mapper.SysPermissionMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysUserMapper;
import com.mes.system.mapper.SysUserRoleMapper;
import com.mes.system.service.SysUserService;
import com.mes.system.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public PageResult<SysUserVO> page(SysUserQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 10 : query.getSize();

        Page<SysUser> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        qw.select(
                SysUser::getId,
                SysUser::getUserCode,
                SysUser::getUserName,
                SysUser::getStatus,
                SysUser::getMustChangePwd,
                SysUser::getCreateTime,
                SysUser::getUpdateTime
        );
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(SysUser::getUserCode, keyword).or().like(SysUser::getUserName, keyword));
        }
        if (StringUtils.hasText(query.getUserCode())) {
            qw.like(SysUser::getUserCode, query.getUserCode().trim());
        }
        if (StringUtils.hasText(query.getUserName())) {
            qw.like(SysUser::getUserName, query.getUserName().trim());
        }
        if (query.getStatus() != null) {
            qw.eq(SysUser::getStatus, query.getStatus());
        }
        qw.orderByDesc(SysUser::getUpdateTime);

        Page<SysUser> result = sysUserMapper.selectPage(page, qw);
        List<SysUser> users = result.getRecords();
        if (users.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, List<String>> roleMap = sysPermissionMapper.selectRoleCodesByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleCodeRow::getUserId,
                        Collectors.mapping(UserRoleCodeRow::getRoleCode, Collectors.toList())
                ));

        List<SysUserVO> records = new ArrayList<>(users.size());
        for (SysUser user : users) {
            SysUserVO vo = new SysUserVO();
            vo.setId(user.getId());
            vo.setUserCode(user.getUserCode());
            vo.setUserName(user.getUserName());
            vo.setStatus(user.getStatus());
            vo.setMustChangePwd(user.getMustChangePwd());
            vo.setRoles(roleMap.getOrDefault(user.getId(), Collections.emptyList()));
            vo.setCreateTime(user.getCreateTime());
            vo.setUpdateTime(user.getUpdateTime());
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public void update(Long id, SysUserUpdateDTO dto) {
        SysUser user = sysUserMapper.selectById(id);
        AssertUtil.notNull(user, "用户不存在");
        user.setUserName(dto.getUserName().trim());
        sysUserMapper.updateById(user);
    }

    @Override
    public void updateStatus(Long id, SysUserStatusDTO dto) {
        AssertUtil.isTrue(dto.getStatus() != null && (dto.getStatus() == 0 || dto.getStatus() == 1), "状态不合法");
        SysUser user = sysUserMapper.selectById(id);
        AssertUtil.notNull(user, "用户不存在");
        AssertUtil.isFalse("admin".equals(user.getUserCode()) && dto.getStatus() == 0, "不可禁用超级管理员");

        user.setStatus(dto.getStatus());
        sysUserMapper.updateById(user);
        if (dto.getStatus() == 0) {
            StpUtil.logout(id);
        }
    }

    @Override
    public List<Long> listRoleIds(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        AssertUtil.notNull(user, "用户不存在");
        return sysUserRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, SysUserRoleAssignDTO dto) {
        SysUser user = sysUserMapper.selectById(id);
        AssertUtil.notNull(user, "用户不存在");

        List<Long> roleIds = dto.getRoleIds() == null
                ? Collections.emptyList()
                : dto.getRoleIds().stream().distinct().toList();

        if (!roleIds.isEmpty()) {
            Long exist = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getId, roleIds)
                    .eq(SysRole::getStatus, 1));
            AssertUtil.isTrue(exist == roleIds.size(), "存在无效或已禁用的角色");
        }

        if ("admin".equals(user.getUserCode())) {
            SysRole adminRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, "admin")
                    .last("LIMIT 1"));
            AssertUtil.notNull(adminRole, "admin 角色不存在");
            AssertUtil.isTrue(roleIds.contains(adminRole.getId()), "超级管理员必须保留 admin 角色");
        }

        sysUserRoleMapper.deleteByUserId(id);
        if (roleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> seen = new HashSet<>();
        for (Long roleId : roleIds) {
            if (!seen.add(roleId)) {
                continue;
            }
            SysUserRole ur = new SysUserRole();
            ur.setId(IdWorker.getId());
            ur.setUserId(id);
            ur.setRoleId(roleId);
            ur.setCreateTime(now);
            sysUserRoleMapper.insert(ur);
        }
    }

    @Override
    public void resetPassword(Long id, SysUserResetPwdDTO dto) {
        SysUser user = sysUserMapper.selectById(id);
        AssertUtil.notNull(user, "用户不存在");
        AssertUtil.notBlank(dto.getPassword(), "临时密码不能为空");
        AssertUtil.isTrue(dto.getPassword().length() >= 6, "临时密码至少6位");

        boolean mustChange = dto.getMustChangePwd() == null || Boolean.TRUE.equals(dto.getMustChangePwd());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setMustChangePwd(mustChange ? 1 : 0);
        sysUserMapper.updateById(user);
        StpUtil.logout(id);
    }

    @Override
    public void kick(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        AssertUtil.notNull(user, "用户不存在");
        AssertUtil.isFalse(StpUtil.getLoginIdAsLong() == id, "不能踢自己下线");
        StpUtil.logout(id);
    }
}
