package com.mes.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.report.facade.ReportFacade;
import com.mes.report.vo.ReportHoldVO;
import com.mes.report.vo.ReportMoveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 报表只读 API */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class MesReportController {

    private final ReportFacade reportFacade;

    @SaCheckPermission("report:view")
    @GetMapping("/move")
    public R<ReportMoveVO> move(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reportFacade.moveSummary(from, to));
    }

    @SaCheckPermission("report:view")
    @GetMapping("/hold")
    public R<ReportHoldVO> hold(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(reportFacade.holdSummary(from, to));
    }
}
