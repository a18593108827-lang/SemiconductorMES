package com.mes.hold.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.hold.dto.MesHoldReasonStatusDTO;
import com.mes.hold.service.HoldReasonService;
import com.mes.hold.vo.MesHoldReasonVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 锁批原因码字典
 */
@RestController
@RequestMapping("/holds/reasons")
@RequiredArgsConstructor
public class HoldReasonController {

    private final HoldReasonService holdReasonService;

    /**
     * 原因码列表。
     * 默认仅启用（Hold 下拉）；all=true 返回含停用（维护用）。
     */
    @SaCheckPermission("hold:list")
    @GetMapping
    public R<List<MesHoldReasonVO>> list(@RequestParam(required = false, defaultValue = "false") boolean all) {
        return R.ok(all ? holdReasonService.listAll() : holdReasonService.listEnabled());
    }

    /** 启停 */
    @SaCheckPermission("hold:create")
    @OperLog(module = "Hold", action = "原因码启停")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody MesHoldReasonStatusDTO dto) {
        holdReasonService.updateStatus(id, dto.getStatus());
        return R.ok();
    }
}
