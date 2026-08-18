package com.mes.recipe.facade;

import com.mes.recipe.vo.MesRecipeVersionDetailVO;
import com.mes.recipe.vo.RecipeResolveVO;

import java.util.Collection;
import java.util.List;

/**
 * 配方对外唯一门面：解析 / 资格 / 版本只读。
 * Track、Dispatch 只依赖本接口，禁止直查 mes_recipe*。
 */
public interface RecipeFacade {

    /**
     * 按 Step + Eqp 解析生效配方版本；无绑定时返回 null。
     * 优先级：Step+eqpId → Step+eqpType → 无。
     */
    RecipeResolveVO resolve(Long stepId, Long eqpId);

    /**
     * 资格校验。require-binding=true 时无解析结果则抛错；默认不强制。
     */
    void assertQualified(Long stepId, Long eqpId);

    /**
     * 批量过滤有资格的设备。
     * 该 Step 无任何绑定时原样返回（一期不拦）。
     */
    List<Long> listQualifiedEqpIds(Long stepId, Collection<Long> eqpIds);

    /** 版本详情（履历展示） */
    MesRecipeVersionDetailVO getVersion(Long versionId);
}
