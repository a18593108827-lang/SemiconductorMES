package com.mes.hold.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.dto.MesHoldQuery;
import com.mes.hold.dto.MesHoldReleaseDTO;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.MesHoldVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 锁批：列表 / 详情 / 发起 / 解锁
 */
@RestController
@RequestMapping("/holds")
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    /**
     * 锁批分页。
     * 默认 status=active；可按 lotNo 关键字、reasonCode 筛选。
     */
    @SaCheckPermission("hold:list")
    @GetMapping
    public R<PageResult<MesHoldVO>> page(MesHoldQuery query) {
        return R.ok(holdService.page(query));
    }

    /** 锁批详情 */
    @SaCheckPermission("hold:list")
    @GetMapping("/{id}")
    public R<MesHoldVO> get(@PathVariable Long id) {
        return R.ok(holdService.get(id));
    }

    /**
     * 发起锁批（Hold）。
     * Lot 须为 wait/processing；写 mes_hold(active)、Lot→held、WIP 同步、tx_log(HOLD)；
     * 同 Lot 仅允许一条 active；原因码 OTHER 须填 remark。
     */
    @SaCheckPermission("hold:create")
    @OperLog(module = "Hold", action = "发起锁批")
    @PostMapping
    public R<MesHoldVO> create(@Valid @RequestBody MesHoldCreateDTO dto) {
        return R.ok(holdService.create(dto));
    }

    /**
     * 解锁（ReleaseHold）。
     * active→released；Lot 恢复 prev_status（缺省 wait）；WIP 同步、tx_log(RELEASE_HOLD)。
     */
    @SaCheckPermission("hold:release")
    @OperLog(module = "Hold", action = "解锁")
    @PostMapping("/{id}/release")
    public R<MesHoldVO> release(@PathVariable Long id, @RequestBody(required = false) MesHoldReleaseDTO dto) {
        String remark = dto != null ? dto.getRemark() : null;
        return R.ok(holdService.release(id, remark));
    }
}
