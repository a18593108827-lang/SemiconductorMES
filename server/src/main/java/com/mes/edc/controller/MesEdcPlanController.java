package com.mes.edc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.edc.dto.MesEdcPlanCreateDTO;
import com.mes.edc.dto.MesEdcPlanEnabledDTO;
import com.mes.edc.dto.MesEdcPlanItemsReplaceDTO;
import com.mes.edc.dto.MesEdcPlanQuery;
import com.mes.edc.dto.MesEdcPlanUpdateDTO;
import com.mes.edc.service.MesEdcPlanService;
import com.mes.edc.vo.MesEdcPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站采集计划接口。
 * items 整表替换挂在 /{id}/items，跟配方绑定那套「一次写清」一个思路。
 */
@RestController
@RequestMapping("/edc/plans")
@RequiredArgsConstructor
public class MesEdcPlanController {

    private final MesEdcPlanService mesEdcPlanService;

    /** 分页列表（列表不带 items 明细，只带 itemCount） */
    @SaCheckPermission("edc:view")
    @GetMapping
    public R<PageResult<MesEdcPlanVO>> page(MesEdcPlanQuery query) {
        return R.ok(mesEdcPlanService.page(query));
    }

    /** 新建：默认启用、门禁先关，配完项再开 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "新建站计划")
    @PostMapping
    public R<MesEdcPlanVO> create(@Valid @RequestBody MesEdcPlanCreateDTO dto) {
        return R.ok(mesEdcPlanService.create(dto));
    }

    /** 整表替换计划项/更新新计划 （放在 /{id} 前避免歧义也行，子路径足够） */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "替换计划项")
    @PutMapping("/{id}/items")
    public R<Void> replaceItems(@PathVariable Long id, @Valid @RequestBody MesEdcPlanItemsReplaceDTO dto) {
        mesEdcPlanService.replaceItems(id, dto);
        return R.ok();
    }

    /** 启停 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "站计划启停")
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody MesEdcPlanEnabledDTO dto) {
        mesEdcPlanService.updateEnabled(id, dto.getEnabled());
        return R.ok();
    }

    /** 详情含计划项 */
    @SaCheckPermission("edc:view")
    @GetMapping("/{id}")
    public R<MesEdcPlanVO> get(@PathVariable Long id) {
        return R.ok(mesEdcPlanService.get(id));
    }

    /** 改门禁开关 / 备注 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "编辑站计划")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesEdcPlanUpdateDTO dto) {
        mesEdcPlanService.update(id, dto);
        return R.ok();
    }
}
