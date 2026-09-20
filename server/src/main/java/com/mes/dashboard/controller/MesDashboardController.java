package com.mes.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.R;
import com.mes.dashboard.facade.DashboardFacade;
import com.mes.dashboard.vo.DashboardOverviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 生产看板只读 API */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class MesDashboardController {

    private final DashboardFacade dashboardFacade;

    @SaCheckPermission("dashboard:view")
    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview(
            @RequestParam(defaultValue = "7") int trendDays,
            @RequestParam(defaultValue = "20") int alarmLimit) {
        return R.ok(dashboardFacade.overview(trendDays, alarmLimit));
    }
}
