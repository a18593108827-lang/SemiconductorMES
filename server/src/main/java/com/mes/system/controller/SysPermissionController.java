package com.mes.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.system.dto.SysPermCreateDTO;
import com.mes.system.dto.SysPermUpdateDTO;
import com.mes.system.service.SysPermissionService;
import com.mes.system.vo.SysPermTreeVO;
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
 * 系统权限接口
 */
@RestController
@RequestMapping("/system/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    @SaCheckPermission("perm:list")
    @GetMapping("/tree")
    public R<List<SysPermTreeVO>> tree() {
        return R.ok(sysPermissionService.tree());
    }

    @SaCheckPermission("perm:add")
    @OperLog(module = "权限", action = "新增")
    @PostMapping
    public R<Void> create(@Valid @RequestBody SysPermCreateDTO dto) {
        sysPermissionService.create(dto);
        return R.ok();
    }

    @SaCheckPermission("perm:edit")
    @OperLog(module = "权限", action = "编辑")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysPermUpdateDTO dto) {
        sysPermissionService.update(id, dto);
        return R.ok();
    }

    @SaCheckPermission("perm:edit")
    @OperLog(module = "权限", action = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysPermissionService.delete(id);
        return R.ok();
    }
}
