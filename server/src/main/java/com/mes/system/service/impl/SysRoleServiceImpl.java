package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.system.dto.RoleUserCountRow;
import com.mes.system.dto.SysRoleCreateDTO;
import com.mes.system.dto.SysRolePermAssignDTO;
import com.mes.system.dto.SysRoleQuery;
import com.mes.system.dto.SysRoleUpdateDTO;
import com.mes.system.entity.SysPermission;
import com.mes.system.entity.SysRole;
import com.mes.system.entity.SysRolePermission;
import com.mes.system.mapper.SysPermMapper;
import com.mes.system.mapper.SysRoleMapper;
import com.mes.system.mapper.SysRolePermissionMapper;
import com.mes.system.service.SysRoleService;
import com.mes.system.vo.SysRoleVO;
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
 * 系统角色服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermMapper sysPermMapper;

    @Override
    public PageResult<SysRoleVO> page(SysRoleQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 10 : query.getSize();

        Page<SysRole> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(SysRole::getRoleCode, keyword).or().like(SysRole::getRoleName, keyword));
        }
        if (StringUtils.hasText(query.getRoleCode())) {
            qw.like(SysRole::getRoleCode, query.getRoleCode().trim());
        }
        if (StringUtils.hasText(query.getRoleName())) {
            qw.like(SysRole::getRoleName, query.getRoleName().trim());
        }
        if (query.getStatus() != null) {
            qw.eq(SysRole::getStatus, query.getStatus());
        }
        qw.orderByAsc(SysRole::getId);

        Page<SysRole> result = sysRoleMapper.selectPage(page, qw);
        List<SysRole> roles = result.getRecords();
        if (roles.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        Map<Long, Long> countMap = sysRoleMapper.selectUserCountByRoleIds(roleIds).stream()
                .collect(Collectors.toMap(RoleUserCountRow::getRoleId, RoleUserCountRow::getCnt, (a, b) -> a));

        List<SysRoleVO> records = new ArrayList<>(roles.size());
        for (SysRole role : roles) {
            SysRoleVO vo = new SysRoleVO();
            vo.setId(role.getId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setRemark(role.getRemark());
            vo.setStatus(role.getStatus());
            vo.setUserCount(countMap.getOrDefault(role.getId(), 0L));
            vo.setCreateTime(role.getCreateTime());
            vo.setUpdateTime(role.getUpdateTime());
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public void create(SysRoleCreateDTO dto) {
        String roleCode = dto.getRoleCode().trim();
        Long count = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        AssertUtil.isTrue(count == 0, "角色编码已存在");
        AssertUtil.isTrue(dto.getStatus() == 0 || dto.getStatus() == 1, "状态不合法");

        SysRole role = new SysRole();
        role.setRoleCode(roleCode);
        role.setRoleName(dto.getRoleName().trim());
        role.setRemark(dto.getRemark() == null ? null : dto.getRemark().trim());
        role.setStatus(dto.getStatus());
        sysRoleMapper.insert(role);
    }

    @Override
    public void update(Long id, SysRoleUpdateDTO dto) {
        SysRole role = sysRoleMapper.selectById(id);
        AssertUtil.notNull(role, "角色不存在");
        AssertUtil.isTrue(dto.getStatus() == 0 || dto.getStatus() == 1, "状态不合法");

        role.setRoleName(dto.getRoleName().trim());
        role.setRemark(dto.getRemark() == null ? null : dto.getRemark().trim());
        role.setStatus(dto.getStatus());
        sysRoleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        AssertUtil.notNull(role, "角色不存在");
        AssertUtil.isFalse("admin".equals(role.getRoleCode()), "不可删除超级管理员角色");

        Long userCount = sysRoleMapper.selectUserCountByRoleIds(List.of(id)).stream()
                .map(RoleUserCountRow::getCnt)
                .findFirst()
                .orElse(0L);
        AssertUtil.isTrue(userCount == 0, "角色下仍有用户，无法删除");

        sysRolePermissionMapper.deleteByRoleId(id);
        role.setRoleCode(role.getRoleCode() + "_del_" + role.getId());
        sysRoleMapper.updateById(role);
        sysRoleMapper.deleteById(id);
    }

    @Override
    public List<Long> listPermissionIds(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        AssertUtil.notNull(role, "角色不存在");
        return sysRolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, SysRolePermAssignDTO dto) {
        SysRole role = sysRoleMapper.selectById(roleId);
        AssertUtil.notNull(role, "角色不存在");

        List<Long> permissionIds = dto.getPermissionIds() == null
                ? Collections.emptyList()
                : dto.getPermissionIds().stream().distinct().toList();

        if (!permissionIds.isEmpty()) {
            Long exist = sysPermMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                    .in(SysPermission::getId, permissionIds)
                    .eq(SysPermission::getStatus, 1));
            AssertUtil.isTrue(exist == permissionIds.size(), "存在无效或已禁用的权限");
        }

        sysRolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> seen = new HashSet<>();
        for (Long permissionId : permissionIds) {
            if (!seen.add(permissionId)) {
                continue;
            }
            SysRolePermission rp = new SysRolePermission();
            rp.setId(IdWorker.getId());
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            rp.setCreateTime(now);
            sysRolePermissionMapper.insert(rp);
        }
    }
}
