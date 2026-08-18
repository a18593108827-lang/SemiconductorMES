package com.mes.route.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.route.dto.MesStepCreateDTO;
import com.mes.route.dto.MesStepQuery;
import com.mes.route.dto.MesStepUpdateDTO;
import com.mes.route.service.MesStepService;
import com.mes.route.vo.MesStepVO;
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
 * 工序接口
 */
@RestController
@RequestMapping("/routes/steps")
@RequiredArgsConstructor
public class MesStepController {

    private final MesStepService mesStepService;

    /** 工序列表 */
    @SaCheckPermission("route:list")
    @GetMapping
    public R<PageResult<MesStepVO>> page(MesStepQuery query) {
        return R.ok(mesStepService.page(query));
    }

    /** 新增工序 */
    @SaCheckPermission("route:add")
    @OperLog(module = "Route", action = "新增工序")
    @PostMapping
    public R<Void> create(@Valid @RequestBody MesStepCreateDTO dto) {
        mesStepService.create(dto);
        return R.ok();
    }

    /** 编辑工序 */
    @SaCheckPermission("route:edit")
    @OperLog(module = "Route", action = "编辑工序")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesStepUpdateDTO dto) {
        mesStepService.update(id, dto);
        return R.ok();
    }
}
