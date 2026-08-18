package com.mes.edc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.edc.dto.MesEdcParamCreateDTO;
import com.mes.edc.dto.MesEdcParamEnabledDTO;
import com.mes.edc.dto.MesEdcParamQuery;
import com.mes.edc.dto.MesEdcParamUpdateDTO;
import com.mes.edc.service.MesEdcParamService;
import com.mes.edc.vo.MesEdcParamVO;
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
 * 量测特性接口。
 * 路径挂在 /edc/params，后面 Spec/Plan 也挂 /edc 下，一块好找。
 */
@RestController
@RequestMapping("/edc/params")
@RequiredArgsConstructor
public class MesEdcParamController {

    private final MesEdcParamService mesEdcParamService;

    /** 分页列表：按编码升序 */
    @SaCheckPermission("edc:view")
    @GetMapping
    public R<PageResult<MesEdcParamVO>> page(MesEdcParamQuery query) {
        return R.ok(mesEdcParamService.page(query));
    }

    /** 新建：默认启用，值类型固定 NUMBER */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "新建特性")
    @PostMapping
    public R<MesEdcParamVO> create(@Valid @RequestBody MesEdcParamCreateDTO dto) {
        return R.ok(mesEdcParamService.create(dto));
    }

    /** 详情 */
    @SaCheckPermission("edc:view")
    @GetMapping("/{id}")
    public R<MesEdcParamVO> get(@PathVariable Long id) {
        return R.ok(mesEdcParamService.get(id));
    }

    /** 改名称/单位/备注，编码不能动 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "编辑特性")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesEdcParamUpdateDTO dto) {
        mesEdcParamService.update(id, dto);
        return R.ok();
    }

    /** 启停：1开 0关 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "特性启停")
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody MesEdcParamEnabledDTO dto) {
        mesEdcParamService.updateEnabled(id, dto.getEnabled());
        return R.ok();
    }
}
