package com.mes.spc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.spc.dto.SpcChartSaveDTO;
import com.mes.spc.dto.SpcEnabledDTO;
import com.mes.spc.dto.SpcLimitsDTO;
import com.mes.spc.facade.SpcFacade;
import com.mes.spc.vo.SpcChartVO;
import com.mes.spc.vo.SpcSeriesVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SPC HTTP 薄封装。只调 SpcFacade，不旁路查表。
 * 查要 spc:view，改要 spc:edit。
 */
@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class MesSpcController {

    private final SpcFacade spcFacade;

    /** 按站 / 特性筛图，都能空 */
    @SaCheckPermission("spc:view")
    @GetMapping("/charts")
    public R<List<SpcChartVO>> listCharts(@RequestParam(required = false) Long stepId,
                                          @RequestParam(required = false) Long paramId) {
        return R.ok(spcFacade.listCharts(stepId, paramId));
    }

    /** 单张图详情；没有就空 */
    @SaCheckPermission("spc:view")
    @GetMapping("/charts/{id}")
    public R<SpcChartVO> getChart(@PathVariable Long id) {
        return R.ok(spcFacade.getChart(id));
    }

    /** 趋势点；规格限只展示，n&lt;25 不给 Cpk */
    @SaCheckPermission("spc:view")
    @GetMapping("/charts/{id}/series")
    public R<SpcSeriesVO> getSeries(@PathVariable Long id,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
                                    @RequestParam(required = false) Integer limit) {
        return R.ok(spcFacade.getSeries(id, from, to, limit));
    }

    /** 新建或改图；有 id 改，没有建 */
    @SaCheckPermission("spc:edit")
    @OperLog(module = "SPC", action = "保存图")
    @PostMapping("/charts")
    public R<SpcChartVO> saveChart(@Valid @RequestBody SpcChartSaveDTO dto) {
        return R.ok(spcFacade.saveChart(dto));
    }

    /** 手填三条控制限 */
    @SaCheckPermission("spc:edit")
    @OperLog(module = "SPC", action = "改控制限")
    @PutMapping("/charts/{id}/limits")
    public R<Void> setLimits(@PathVariable Long id, @RequestBody SpcLimitsDTO dto) {
        spcFacade.setLimits(id, dto.getUcl(), dto.getCl(), dto.getLcl());
        return R.ok();
    }

    /** 启停：1开 0关 */
    @SaCheckPermission("spc:edit")
    @OperLog(module = "SPC", action = "图启停")
    @PutMapping("/charts/{id}/enabled")
    public R<Void> enable(@PathVariable Long id, @Valid @RequestBody SpcEnabledDTO dto) {
        spcFacade.enable(id, dto.getEnabled() != null && dto.getEnabled() == 1);
        return R.ok();
    }
}
