package com.mes.recipe.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.recipe.dto.MesRecipeBindingCreateDTO;
import com.mes.recipe.dto.MesRecipeBindingQuery;
import com.mes.recipe.service.MesRecipeBindingService;
import com.mes.recipe.vo.MesRecipeBindingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配方绑定：Step×Eqp → Recipe
 */
@RestController
@RequestMapping("/recipe-bindings")
@RequiredArgsConstructor
public class MesRecipeBindingController {

    private final MesRecipeBindingService mesRecipeBindingService;

    /** 分页列表 */
    @SaCheckPermission("recipe:view")
    @GetMapping
    public R<PageResult<MesRecipeBindingVO>> page(MesRecipeBindingQuery query) {
        return R.ok(mesRecipeBindingService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("recipe:view")
    @GetMapping("/{id}")
    public R<MesRecipeBindingVO> get(@PathVariable Long id) {
        return R.ok(mesRecipeBindingService.get(id));
    }

    /** 新建绑定 */
    @SaCheckPermission("recipe:bind")
    @OperLog(module = "Recipe", action = "新建配方绑定")
    @PostMapping
    public R<MesRecipeBindingVO> create(@Valid @RequestBody MesRecipeBindingCreateDTO dto) {
        return R.ok(mesRecipeBindingService.create(dto));
    }

    /** 删除绑定（逻辑删） */
    @SaCheckPermission("recipe:bind")
    @OperLog(module = "Recipe", action = "删除配方绑定")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mesRecipeBindingService.delete(id);
        return R.ok();
    }
}
