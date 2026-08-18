package com.mes.dispatch.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.dispatch.dto.DispatchReserveCreateDTO;
import com.mes.dispatch.dto.DispatchReserveReleaseDTO;
import com.mes.dispatch.service.DispatchService;
import com.mes.dispatch.vo.DispatchCandidatesVO;
import com.mes.dispatch.vo.DispatchReserveVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 派工：候选 / 推荐 / 预约
 */
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    /**
     * 按 Lot 返回可开工候选机 + recommendedEqpId。
     * 现场台可用 track:view。
     */
    @SaCheckPermission(value = {"dispatch:view", "track:view"}, mode = SaMode.OR)
    @GetMapping("/candidates")
    public R<DispatchCandidatesVO> candidates(@RequestParam Long lotId) {
        return R.ok(dispatchService.listCandidates(lotId));
    }

    /** 预约设备（默认 30 分钟超时）
     * 校验
     *
     * 批次存在，且状态是 wait / processing
     * 无有效 Hold
     * 设备可用（assertUsable）
     * 站要求的 eqpType 与设备类型一致（有类型时）
     * 该设备未被其他批次的有效预约占用
     * 写入
     *
     * 已约同一台 → 直接返回
     * 已约其他台 → 先释旧约，再插新约（active，带过期时间）
     * */
    @SaCheckPermission("dispatch:reserve")
    @OperLog(module = "Dispatch", action = "预约设备")
    @PostMapping("/reserve")
    public R<DispatchReserveVO> reserve(@Valid @RequestBody DispatchReserveCreateDTO dto) {
        return R.ok(dispatchService.reserve(dto));
    }

    /** 人工释约 */
    @SaCheckPermission("dispatch:reserve")
    @OperLog(module = "Dispatch", action = "释约设备")
    @PostMapping("/reserves/{id}/release")
    public R<DispatchReserveVO> release(
            @PathVariable Long id,
            @RequestBody(required = false) DispatchReserveReleaseDTO dto) {
        String remark = dto != null ? dto.getRemark() : null;
        return R.ok(dispatchService.release(id, remark));
    }

    /**
     * 查预约列表。
     * lotId / eqpId 至少传一个；status 默认 active。
     */
    @SaCheckPermission(value = {"dispatch:view", "track:view"}, mode = SaMode.OR)
    @GetMapping("/reserves")
    public R<List<DispatchReserveVO>> reserves(
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long eqpId,
            @RequestParam(required = false) String status) {
        return R.ok(dispatchService.listReserves(lotId, eqpId, status));
    }
}
