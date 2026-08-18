package com.mes.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.equipment.dto.MesEqpCreateDTO;
import com.mes.equipment.dto.MesEqpEnabledDTO;
import com.mes.equipment.dto.MesEqpQuery;
import com.mes.equipment.dto.MesEqpStatusDTO;
import com.mes.equipment.dto.MesEqpUpdateDTO;
import com.mes.equipment.service.MesEqpService;
import com.mes.equipment.vo.MesEqpOptionVO;
import com.mes.equipment.vo.MesEqpVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备：主数据 CRUD / 启停 / 改态 / 选机 options
 */
@RestController
@RequestMapping("/equipments")
@RequiredArgsConstructor
public class MesEqpController {

    private final MesEqpService mesEqpService;

    /** 分页列表 */
    @SaCheckPermission("eqp:list")
    @GetMapping
    public R<PageResult<MesEqpVO>> page(MesEqpQuery query) {
        return R.ok(mesEqpService.page(query));
    }

    /**
     * 选机下拉：仅启用且 idle/running。
     * 可选 eqpType 过滤；现场台可用 track:view。
     */
    @SaCheckPermission(value = {"eqp:list", "track:view"}, mode = SaMode.OR)
    @GetMapping("/options")
    public R<List<MesEqpOptionVO>> options(@RequestParam(required = false) String eqpType) {
        return R.ok(mesEqpService.listOptions(eqpType));
    }

    /** 详情 */
    @SaCheckPermission("eqp:list")
    @GetMapping("/{id}")
    public R<MesEqpVO> get(@PathVariable Long id) {
        return R.ok(mesEqpService.get(id));
    }

    /** 新建（默认 idle + 启用） */
    @SaCheckPermission("eqp:add")
    @OperLog(module = "Equipment", action = "新建设备")
    @PostMapping
    public R<MesEqpVO> create(@Valid @RequestBody MesEqpCreateDTO dto) {
        return R.ok(mesEqpService.create(dto));
    }

    /** 改主数据（不含业务态） */
    @SaCheckPermission("eqp:edit")
    @OperLog(module = "Equipment", action = "编辑设备")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesEqpUpdateDTO dto) {
        mesEqpService.update(id, dto);
        return R.ok();
    }

    /** 启停（逻辑停用） */
    @SaCheckPermission("eqp:edit")
    @OperLog(module = "Equipment", action = "设备启停")
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody MesEqpEnabledDTO dto) {
        mesEqpService.updateEnabled(id, dto.getEnabled());
        return R.ok();
    }

    /**
     * 业务态改态。
     * idle/running/down/pm/eng/offline；走乐观锁。
     */
    @SaCheckPermission("eqp:status")
    @OperLog(module = "Equipment", action = "设备改态")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody MesEqpStatusDTO dto) {
        mesEqpService.updateStatus(id, dto.getStatus());
        return R.ok();
    }
}
