package com.mes.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.system.dto.SysPermCreateDTO;
import com.mes.system.dto.SysPermUpdateDTO;
import com.mes.system.entity.SysPermission;
import com.mes.system.mapper.SysPermMapper;
import com.mes.system.mapper.SysRolePermissionMapper;
import com.mes.system.service.SysPermissionService;
import com.mes.system.vo.SysPermTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限服务实现
 */
@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermMapper sysPermMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    public List<SysPermTreeVO> tree() {
        List<SysPermission> list = sysPermMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortNo)
                .orderByAsc(SysPermission::getId));

        Map<Long, SysPermTreeVO> map = new HashMap<>();
        for (SysPermission p : list) {
            SysPermTreeVO vo = new SysPermTreeVO();
            vo.setId(p.getId());
            vo.setParentId(p.getParentId());
            vo.setPermType(p.getPermType());
            vo.setPermCode(p.getPermCode());
            vo.setPermName(p.getPermName());
            vo.setPath(p.getPath());
            vo.setIcon(p.getIcon());
            vo.setSortNo(p.getSortNo());
            vo.setStatus(p.getStatus());
            map.put(p.getId(), vo);
        }

        List<SysPermTreeVO> roots = new ArrayList<>();
        for (SysPermTreeVO vo : map.values()) {
            Long parentId = vo.getParentId() == null ? 0L : vo.getParentId();
            if (parentId == 0L || !map.containsKey(parentId)) {
                roots.add(vo);
            } else {
                map.get(parentId).getChildren().add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    @Override
    public void create(SysPermCreateDTO dto) {
        validateTypeStatus(dto.getPermType(), dto.getStatus());
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        assertParentOk(parentId, null);

        String permCode = normalizeCode(dto.getPermCode());
        assertCodeUnique(permCode, null);

        SysPermission perm = new SysPermission();
        perm.setParentId(parentId);
        perm.setPermType(dto.getPermType());
        perm.setPermCode(permCode);
        perm.setPermName(dto.getPermName().trim());
        perm.setPath(normalizePath(dto.getPath()));
        perm.setIcon(normalizePath(dto.getIcon()));
        perm.setSortNo(dto.getSortNo());
        perm.setStatus(dto.getStatus());
        sysPermMapper.insert(perm);
    }

    @Override
    public void update(Long id, SysPermUpdateDTO dto) {
        SysPermission perm = sysPermMapper.selectById(id);
        AssertUtil.notNull(perm, "权限不存在");
        validateTypeStatus(dto.getPermType(), dto.getStatus());

        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        AssertUtil.isFalse(id.equals(parentId), "父级不能是自己");
        assertParentOk(parentId, id);
        AssertUtil.isFalse(isDescendant(id, parentId), "父级不能是自己的子节点");

        String permCode = normalizeCode(dto.getPermCode());
        assertCodeUnique(permCode, id);

        perm.setParentId(parentId);
        perm.setPermType(dto.getPermType());
        perm.setPermCode(permCode);
        perm.setPermName(dto.getPermName().trim());
        perm.setPath(normalizePath(dto.getPath()));
        perm.setIcon(normalizePath(dto.getIcon()));
        perm.setSortNo(dto.getSortNo());
        perm.setStatus(dto.getStatus());
        sysPermMapper.updateById(perm);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysPermission perm = sysPermMapper.selectById(id);
        AssertUtil.notNull(perm, "权限不存在");

        Long childCount = sysPermMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id));
        AssertUtil.isTrue(childCount == 0, "存在子权限，无法删除");

        sysRolePermissionMapper.deleteByPermissionId(id);
        if (StringUtils.hasText(perm.getPermCode())) {
            perm.setPermCode(perm.getPermCode() + "_del_" + perm.getId());
            sysPermMapper.updateById(perm);
        }
        sysPermMapper.deleteById(id);
    }

    private void validateTypeStatus(Integer permType, Integer status) {
        AssertUtil.isTrue(permType != null && permType >= 1 && permType <= 3, "类型不合法");
        AssertUtil.isTrue(status != null && (status == 0 || status == 1), "状态不合法");
    }

    private void assertParentOk(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        SysPermission parent = sysPermMapper.selectById(parentId);
        AssertUtil.notNull(parent, "父级权限不存在");
        AssertUtil.isTrue(parent.getPermType() == null || parent.getPermType() != 3, "按钮下不能再挂子权限");
        if (selfId != null) {
            AssertUtil.isFalse(selfId.equals(parentId), "父级不能是自己");
        }
    }

    private void assertCodeUnique(String permCode, Long excludeId) {
        if (!StringUtils.hasText(permCode)) {
            return;
        }
        LambdaQueryWrapper<SysPermission> qw = new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermCode, permCode);
        if (excludeId != null) {
            qw.ne(SysPermission::getId, excludeId);
        }
        AssertUtil.isTrue(sysPermMapper.selectCount(qw) == 0, "权限码已存在");
    }

    private boolean isDescendant(Long ancestorId, Long maybeChildId) {
        if (maybeChildId == null || maybeChildId == 0L) {
            return false;
        }
        Set<Long> visited = new HashSet<>();
        Long current = maybeChildId;
        while (current != null && current != 0L && visited.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            SysPermission node = sysPermMapper.selectById(current);
            if (node == null) {
                return false;
            }
            current = node.getParentId();
        }
        return false;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return code.trim();
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        return path.trim();
    }

    private void sortTree(List<SysPermTreeVO> nodes) {
        nodes.sort(Comparator
                .comparing(SysPermTreeVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SysPermTreeVO::getId));
        for (SysPermTreeVO n : nodes) {
            sortTree(n.getChildren());
        }
    }
}
