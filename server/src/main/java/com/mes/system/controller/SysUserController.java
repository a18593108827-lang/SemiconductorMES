package com.mes.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.dto.SysUserResetPwdDTO;
import com.mes.system.dto.SysUserRoleAssignDTO;
import com.mes.system.dto.SysUserStatusDTO;
import com.mes.system.dto.SysUserUpdateDTO;
import com.mes.system.service.SysUserService;
import com.mes.system.vo.SysUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统用户接口
 */
@RestController
@RequestMapping("/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @SaCheckPermission("user:list")
    @GetMapping
    public R<PageResult<SysUserVO>> page(SysUserQuery query) {
        return R.ok(sysUserService.page(query));
    }

    @SaCheckPermission("user:edit")
    @OperLog(module = "用户", action = "编辑")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserUpdateDTO dto) {
        sysUserService.update(id, dto);
        return R.ok();
    }

    @SaCheckPermission("user:edit")
    @OperLog(module = "用户", action = "启停")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody SysUserStatusDTO dto) {
        sysUserService.updateStatus(id, dto);
        return R.ok();
    }

    @SaCheckPermission("user:list")
    @GetMapping("/{id}/roles")
    public R<List<Long>> listRoles(@PathVariable Long id) {
        return R.ok(sysUserService.listRoleIds(id));
    }

    @SaCheckPermission("user:assign-role")
    @OperLog(module = "用户", action = "分配角色")
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody SysUserRoleAssignDTO dto) {
        sysUserService.assignRoles(id, dto);
        return R.ok();
    }

    @SaCheckPermission("user:reset-pwd")
    @OperLog(module = "用户", action = "重置密码")
    @PutMapping("/{id}/password/reset")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody SysUserResetPwdDTO dto) {
        sysUserService.resetPassword(id, dto);
        return R.ok();
    }

    @SaCheckPermission("user:kick")
    @OperLog(module = "用户", action = "踢人下线")
    @PostMapping("/{id}/kick")
    public R<Void> kick(@PathVariable Long id) {
        sysUserService.kick(id);
        return R.ok();
    }
}
