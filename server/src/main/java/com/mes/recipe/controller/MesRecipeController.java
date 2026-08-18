package com.mes.recipe.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeEnabledDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.dto.MesRecipeVersionCreateDTO;
import com.mes.recipe.dto.MesRecipeVersionUpdateDTO;
import com.mes.recipe.facade.RecipeFacade;
import com.mes.recipe.service.MesRecipeService;
import com.mes.recipe.vo.MesRecipeVO;
import com.mes.recipe.vo.MesRecipeVersionDetailVO;
import com.mes.recipe.vo.MesRecipeVersionVO;
import com.mes.recipe.vo.RecipeResolveVO;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方：主数据 CRUD / 启停；版本草稿 / 发布；解析
 * <p>
 * 注意：{@code /versions/**}、{@code /resolve} 映射放在 {@code /{id}} 之前，避免路径冲突。
 */
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class MesRecipeController {

    private final MesRecipeService mesRecipeService;
    private final RecipeFacade recipeFacade;

    /** 分页列表 */
    @SaCheckPermission("recipe:view")
    @GetMapping
    public R<PageResult<MesRecipeVO>> page(MesRecipeQuery query) {
        return R.ok(mesRecipeService.page(query));
    }

    /** 新建（默认启用；recipeCode 唯一） */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "新建配方")
    @PostMapping
    public R<MesRecipeVO> create(@Valid @RequestBody MesRecipeCreateDTO dto) {
        return R.ok(mesRecipeService.create(dto));
    }

    /** 解析：Step + Eqp → 生效配方版本（无绑定返回 data=null） */
    @SaCheckPermission(value = {"recipe:view", "track:view"}, mode = SaMode.OR)
    @GetMapping("/resolve")
    public R<RecipeResolveVO> resolve(@RequestParam Long stepId, @RequestParam Long eqpId) {
        return R.ok(recipeFacade.resolve(stepId, eqpId));
    }

    /** 版本详情 */
    @SaCheckPermission("recipe:view")
    @GetMapping("/versions/{versionId}")
    public R<MesRecipeVersionDetailVO> getVersion(@PathVariable Long versionId) {
        return R.ok(mesRecipeService.getVersion(versionId));
    }

    /** 编辑草稿 */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "编辑配方草稿")
    @PutMapping("/versions/{versionId}")
    public R<Void> updateDraft(@PathVariable Long versionId,
                               @Valid @RequestBody MesRecipeVersionUpdateDTO dto) {
        mesRecipeService.updateDraft(versionId, dto);
        return R.ok();
    }

    /** 发布版本 */
    @SaCheckPermission("recipe:publish")
    @OperLog(module = "Recipe", action = "发布配方版本")
    @PostMapping("/versions/{versionId}/publish")
    public R<Void> publish(@PathVariable Long versionId) {
        mesRecipeService.publish(versionId);
        return R.ok();
    }

    /** 详情 */
    @SaCheckPermission("recipe:view")
    @GetMapping("/{id}")
    public R<MesRecipeVO> get(@PathVariable Long id) {
        return R.ok(mesRecipeService.get(id));
    }

    /** 版本列表 */
    @SaCheckPermission("recipe:view")
    @GetMapping("/{id}/versions")
    public R<List<MesRecipeVersionVO>> listVersions(@PathVariable Long id) {
        return R.ok(mesRecipeService.listVersions(id));
    }

    /** 新建草稿（同配方仅一个 draft） */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "新建配方草稿")
    @PostMapping("/{id}/versions")
    public R<Map<String, Long>> createDraft(@PathVariable Long id,
                                            @RequestBody(required = false) MesRecipeVersionCreateDTO dto) {
        Long versionId = mesRecipeService.createDraft(id, dto != null ? dto : new MesRecipeVersionCreateDTO());
        Map<String, Long> data = new HashMap<>(1);
        data.put("versionId", versionId);
        return R.ok(data);
    }

    /** 改主数据（不含编码） */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "编辑配方")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesRecipeUpdateDTO dto) {
        mesRecipeService.update(id, dto);
        return R.ok();
    }

    /** 启停 */
    @SaCheckPermission("recipe:edit")
    @OperLog(module = "Recipe", action = "配方启停")
    @PutMapping("/{id}/enabled")
    public R<Void> updateEnabled(@PathVariable Long id, @Valid @RequestBody MesRecipeEnabledDTO dto) {
        mesRecipeService.updateEnabled(id, dto.getEnabled());
        return R.ok();
    }
}
