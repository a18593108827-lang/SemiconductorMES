package com.mes.wip.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.wip.dto.MesWipQuery;
import com.mes.wip.service.WipService;
import com.mes.wip.vo.MesWipStepSummaryVO;
import com.mes.wip.vo.MesWipVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 在制只读查询 */
@RestController
@RequestMapping("/wip")
@RequiredArgsConstructor
public class WipController {

    private final WipService wipService;

    @SaCheckPermission("wip:list")
    @GetMapping
    public R<PageResult<MesWipVO>> page(MesWipQuery query) {
        return R.ok(wipService.page(query));
    }

    @SaCheckPermission("wip:list")
    @GetMapping("/summary/by-step")
    public R<List<MesWipStepSummaryVO>> summaryByStep() {
        return R.ok(wipService.summaryByStep());
    }
}
