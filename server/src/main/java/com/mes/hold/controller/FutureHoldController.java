package com.mes.hold.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.hold.dto.MesFutureHoldCancelDTO;
import com.mes.hold.dto.MesFutureHoldCreateDTO;
import com.mes.hold.dto.MesFutureHoldQuery;
import com.mes.hold.service.FutureHoldService;
import com.mes.hold.vo.MesFutureHoldVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约锁批（Future Hold）：列表 / 详情 / 设置 / 取消。
 * 到站激活由 Track 钩子调用 FutureHoldService.tryActivate，不经本 Controller。
 */
@RestController
@RequestMapping("/holds/future")
@RequiredArgsConstructor
public class FutureHoldController {

    private final FutureHoldService futureHoldService;

    /**
     * 预约锁批分页。
     * 默认 status=pending；可传 all / activated / cancelled；支持 lotNo 关键字、reasonCode。
     */
    @SaCheckPermission("hold:list")
    @GetMapping
    public R<PageResult<MesFutureHoldVO>> page(MesFutureHoldQuery query) {
        return R.ok(futureHoldService.page(query));
    }

    /** 预约锁批详情 */
    @SaCheckPermission("hold:list")
    @GetMapping("/{id}")
    public R<MesFutureHoldVO> get(@PathVariable Long id) {
        return R.ok(futureHoldService.get(id));
    }

    /**
     * 设置预约锁批。
     * 绑 Lot 放行快照 route_version_id + targetSortNo；pending 不拦截 Track；
     * OTHER 须备注；同 (lot, version, sort, timing) 仅一条 pending。
     */
    @SaCheckPermission("hold:create")
    @OperLog(module = "Hold", action = "预约锁批")
    @PostMapping
    public R<MesFutureHoldVO> create(@Valid @RequestBody MesFutureHoldCreateDTO dto) {
        return R.ok(futureHoldService.create(dto));
    }

    /**
     * 取消未生效预约。
     * 仅 pending 可取消；已激活须走 ReleaseHold。
     */
    @SaCheckPermission("hold:create")
    @OperLog(module = "Hold", action = "取消预约锁批")
    @PostMapping("/{id}/cancel")
    public R<MesFutureHoldVO> cancel(@PathVariable Long id,
                                     @RequestBody(required = false) MesFutureHoldCancelDTO dto) {
        String remark = dto != null ? dto.getRemark() : null;
        return R.ok(futureHoldService.cancel(id, remark));
    }
}
