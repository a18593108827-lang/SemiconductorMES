package com.mes.edc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.edc.dto.MesEdcSpecCreateDTO;
import com.mes.edc.dto.MesEdcSpecQuery;
import com.mes.edc.dto.MesEdcSpecUpdateDTO;
import com.mes.edc.service.MesEdcSpecService;
import com.mes.edc.vo.MesEdcSpecVO;
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
 * 量测规格接口。
 * 草稿用 edit；发布单独要 publish 权限，避免随便把限值改生效。
 */
@RestController
@RequestMapping("/edc/specs")
@RequiredArgsConstructor
public class MesEdcSpecController {

    private final MesEdcSpecService mesEdcSpecService;

    /** 分页：可按特性 / 产品 / 状态筛 */
    @SaCheckPermission("edc:view")
    @GetMapping
    public R<PageResult<MesEdcSpecVO>> page(MesEdcSpecQuery query) {
        return R.ok(mesEdcSpecService.page(query));
    }

    /** 新建草稿（同特性+产品只能有一份草稿） */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "新建规格草稿")
    @PostMapping
    public R<MesEdcSpecVO> create(@Valid @RequestBody MesEdcSpecCreateDTO dto) {
        return R.ok(mesEdcSpecService.create(dto));
    }

    /** 发布要放在 /{id} 前面？publish 是子路径，Spring 能区分，OK */
    @SaCheckPermission("edc:publish")
    @OperLog(module = "EDC", action = "发布规格")
    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        mesEdcSpecService.publish(id);
        return R.ok();
    }

    /** 详情 */
    @SaCheckPermission("edc:view")
    @GetMapping("/{id}")
    public R<MesEdcSpecVO> get(@PathVariable Long id) {
        return R.ok(mesEdcSpecService.get(id));
    }

    /** 改草稿上下限 */
    @SaCheckPermission("edc:edit")
    @OperLog(module = "EDC", action = "编辑规格草稿")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesEdcSpecUpdateDTO dto) {
        mesEdcSpecService.update(id, dto);
        return R.ok();
    }
}
