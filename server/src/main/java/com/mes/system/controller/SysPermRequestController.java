package com.mes.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.system.dto.PermRequestCreateDTO;
import com.mes.system.dto.PermRequestDecideDTO;
import com.mes.system.dto.PermRequestQuery;
import com.mes.system.service.SysPermRequestService;
import com.mes.system.vo.ApplyableRoleVO;
import com.mes.system.vo.PermRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限申请 / 审批接口。
 * <p>
 * 一期粒度：申请「角色」（非零散权限码）。超管仍可在用户管理直接赋角，不强制走本流程。
 * </p>
 * <p>
 * 申请单状态：{@code pending} → {@code approved} / {@code rejected} / {@code cancelled}。
 * </p>
 */
@RestController
@RequestMapping("/system/perm-requests")
@RequiredArgsConstructor
public class SysPermRequestController {

    private final SysPermRequestService permRequestService;

    /**
     * 可申请角色列表（发起申请下拉用）。
     * <p>排除 {@code admin}、仅返回启用角色。权限：{@code perm:apply}。</p>
     */
    @SaCheckPermission("perm:apply")
    @GetMapping("/applyable-roles")
    public R<List<ApplyableRoleVO>> applyableRoles() {
        return R.ok(permRequestService.listApplyableRoles());
    }

    /**
     * 发起申请。
     * <p>
     * Body：{@code roleId}、{@code reason}。同一用户对同一角色仅允许一笔 {@code pending}；
     * 不可申请 admin。权限：{@code perm:apply}。
     * </p>
     */
    @SaCheckPermission("perm:apply")
    @OperLog(module = "权限申请", action = "发起申请")
    @PostMapping
    public R<Void> create(@Valid @RequestBody PermRequestCreateDTO dto) {
        permRequestService.create(dto);
        return R.ok();
    }

    /**
     * 我的申请分页。
     * <p>
     * 仅返回当前登录人申请单。Query：{@code status}（可选）、{@code page}、{@code size}。
     * 权限：{@code system:perm-apply}（菜单码，与「我的申请」页入口一致）。
     * </p>
     */
    @SaCheckPermission("system:perm-apply")
    @GetMapping("/mine")
    public R<PageResult<PermRequestVO>> mine(PermRequestQuery query) {
        return R.ok(permRequestService.pageMine(query));
    }

    /**
     * 撤回申请。
     * <p>仅申请人本人、且状态为 {@code pending} 可撤回。权限：{@code perm:apply}。</p>
     */
    @SaCheckPermission("perm:apply")
    @OperLog(module = "权限申请", action = "撤回申请")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        permRequestService.cancel(id);
        return R.ok();
    }

    /**
     * 待审批列表（全库 pending）。
     * <p>任一具备 {@code perm:approve} 的用户可见并可处理。Query：{@code page}、{@code size}。</p>
     */
    @SaCheckPermission("perm:approve")
    @GetMapping("/todo")
    public R<PageResult<PermRequestVO>> todo(PermRequestQuery query) {
        return R.ok(permRequestService.pageTodo(query));
    }

    /**
     * 我已处理列表（当前用户作为审批人的通过/驳回单）。
     * <p>权限：{@code perm:approve}。</p>
     */
    @SaCheckPermission("perm:approve")
    @GetMapping("/done")
    public R<PageResult<PermRequestVO>> done(PermRequestQuery query) {
        return R.ok(permRequestService.pageDone(query));
    }

    /**
     * 审批通过。
     * <p>
     * Body 可选：{@code opinion}（空则默认「同意」）。CAS 仅 {@code pending → approved}；
     * 事务内写入 {@code sys_user_role}（已有则幂等），并踢申请人下线以刷新权限。
     * 不能审批本人申请。权限：{@code perm:approve}。
     * </p>
     */
    @SaCheckPermission("perm:approve")
    @OperLog(module = "权限申请", action = "审批通过")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) PermRequestDecideDTO dto) {
        permRequestService.approve(id, dto == null ? new PermRequestDecideDTO() : dto);
        return R.ok();
    }

    /**
     * 审批驳回。
     * <p>
     * Body 可选：{@code opinion}（空则默认「驳回」）。CAS 仅 {@code pending → rejected}；不赋权。
     * 不能审批本人申请。权限：{@code perm:approve}。
     * </p>
     */
    @SaCheckPermission("perm:approve")
    @OperLog(module = "权限申请", action = "审批驳回")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) PermRequestDecideDTO dto) {
        permRequestService.reject(id, dto == null ? new PermRequestDecideDTO() : dto);
        return R.ok();
    }
}
