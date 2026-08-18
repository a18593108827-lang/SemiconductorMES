package com.mes.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.system.dto.SysRoleCreateDTO;
import com.mes.system.dto.SysRolePermAssignDTO;
import com.mes.system.dto.SysRoleQuery;
import com.mes.system.dto.SysRoleUpdateDTO;
import com.mes.system.service.SysRoleService;
import com.mes.system.vo.SysRoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统角色接口
 */
@RestController
@RequestMapping("/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @SaCheckPermission("role:list")
    @GetMapping
    public R<PageResult<SysRoleVO>> page(SysRoleQuery query) {
        return R.ok(sysRoleService.page(query));
    }

    @SaCheckPermission("role:add")
    @OperLog(module = "角色", action = "新增")
    @PostMapping
    public R<Void> create(@Valid @RequestBody SysRoleCreateDTO dto) {
        sysRoleService.create(dto);
        return R.ok();
    }

    @SaCheckPermission("role:edit")
    @OperLog(module = "角色", action = "编辑")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysRoleUpdateDTO dto) {
        sysRoleService.update(id, dto);
        return R.ok();
    }

    @SaCheckPermission("role:edit")
    @OperLog(module = "角色", action = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return R.ok();
    }

    @SaCheckPermission("role:list")
    @GetMapping("/{id}/permissions")
    public R<List<Long>> listPermissions(@PathVariable Long id) {
        return R.ok(sysRoleService.listPermissionIds(id));
    }

    @SaCheckPermission("role:assign-perm")
    @OperLog(module = "角色", action = "分配权限")
    @PutMapping("/{id}/permissions")
    public R<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody SysRolePermAssignDTO dto) {
        sysRoleService.assignPermissions(id, dto);
        return R.ok();
    }
}
