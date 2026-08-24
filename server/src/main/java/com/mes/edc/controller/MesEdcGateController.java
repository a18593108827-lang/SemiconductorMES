package com.mes.edc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.R;
import com.mes.edc.facade.EdcFacade;
import com.mes.edc.vo.EdcGateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 量测门禁预检。TrackOut 不走这里，只调 EdcFacade。
 */
@RestController
@RequestMapping("/edc")
@RequiredArgsConstructor
public class MesEdcGateController {

    private final EdcFacade edcFacade;

    @SaCheckPermission(value = {"edc:view", "track:view"}, mode = SaMode.OR)
    @GetMapping("/gate")
    public R<EdcGateResult> gate(@RequestParam Long lotId, @RequestParam Long stepId) {
        return R.ok(edcFacade.evaluateGate(lotId, null, null, stepId));
    }
}
