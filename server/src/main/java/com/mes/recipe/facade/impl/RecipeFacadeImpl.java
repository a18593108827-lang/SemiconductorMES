package com.mes.recipe.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.recipe.entity.MesRecipe;
import com.mes.recipe.entity.MesRecipeBinding;
import com.mes.recipe.entity.MesRecipeVersion;
import com.mes.recipe.facade.RecipeFacade;
import com.mes.recipe.mapper.MesRecipeBindingMapper;
import com.mes.recipe.mapper.MesRecipeMapper;
import com.mes.recipe.mapper.MesRecipeVersionMapper;
import com.mes.recipe.service.MesRecipeService;
import com.mes.recipe.service.impl.MesRecipeServiceImpl;
import com.mes.recipe.vo.MesRecipeVersionDetailVO;
import com.mes.recipe.vo.RecipeResolveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 配方解析门面实现 */
@Service
@RequiredArgsConstructor
public class RecipeFacadeImpl implements RecipeFacade {

    private static final int ENABLED = 1;

    private final MesRecipeBindingMapper mesRecipeBindingMapper;
    private final MesRecipeMapper mesRecipeMapper;
    private final MesRecipeVersionMapper mesRecipeVersionMapper;
    private final MesEqpMapper mesEqpMapper;
    private final MesRecipeService mesRecipeService;

    /** true=无绑定/无解析结果时 TrackIn 拒绝；默认 false */
    @Value("${mes.recipe.require-binding:false}")
    private boolean requireBinding;

    @Override
    public RecipeResolveVO resolve(Long stepId, Long eqpId) {
        if (stepId == null || eqpId == null) {
            return null;
        }
        MesEqp eqp = mesEqpMapper.selectById(eqpId);
        if (eqp == null) {
            return null;
        }

        MesRecipeBinding binding = findBinding(stepId, eqpId);
        if (binding == null && StringUtils.hasText(eqp.getEqpType())) {
            binding = findBindingByType(stepId, eqp.getEqpType().trim());
        }
        if (binding == null) {
            return null;
        }

        MesRecipe recipe = mesRecipeMapper.selectById(binding.getRecipeId());
        if (recipe == null || !Objects.equals(recipe.getEnabled(), ENABLED)) {
            return null;
        }

        // 获取配方版本
        MesRecipeVersion version = resolveVersion(binding);
        if (version == null) {
            return null;
        }

        RecipeResolveVO vo = new RecipeResolveVO();
        vo.setRecipeId(recipe.getId());
        vo.setRecipeCode(recipe.getRecipeCode());
        vo.setRecipeName(recipe.getRecipeName());
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setStepId(stepId);
        vo.setEqpId(eqpId);
        return vo;
    }

    @Override
    public void assertQualified(Long stepId, Long eqpId) {
        if (!requireBinding) {
            return;
        }
        if (stepId == null || eqpId == null) {
            return;
        }
        RecipeResolveVO hit = resolve(stepId, eqpId);
        AssertUtil.notNull(hit, "设备无配方资格，无法开工");
    }

    @Override
    public List<Long> listQualifiedEqpIds(Long stepId, Collection<Long> eqpIds) {
        if (eqpIds == null || eqpIds.isEmpty()) {
            return List.of();
        }
        List<Long> input = new ArrayList<>(eqpIds);
        if (stepId == null) {
            return input;
        }
        Long bindCount = mesRecipeBindingMapper.selectCount(new LambdaQueryWrapper<MesRecipeBinding>()
                .eq(MesRecipeBinding::getStepId, stepId)
                .eq(MesRecipeBinding::getEnabled, ENABLED));
        if (bindCount == null || bindCount == 0) {
            return input;
        }
        List<Long> qualified = new ArrayList<>();
        for (Long eqpId : input) {
            if (resolve(stepId, eqpId) != null) {
                qualified.add(eqpId);
            }
        }
        return qualified;
    }

    @Override
    public MesRecipeVersionDetailVO getVersion(Long versionId) {
        return mesRecipeService.getVersion(versionId);
    }

    private MesRecipeBinding findBinding(Long stepId, Long eqpId) {
        return mesRecipeBindingMapper.selectOne(new LambdaQueryWrapper<MesRecipeBinding>()
                .eq(MesRecipeBinding::getStepId, stepId)
                .eq(MesRecipeBinding::getEqpId, eqpId)
                .eq(MesRecipeBinding::getEnabled, ENABLED)
                .last("LIMIT 1"));
    }

    private MesRecipeBinding findBindingByType(Long stepId, String eqpType) {
        return mesRecipeBindingMapper.selectOne(new LambdaQueryWrapper<MesRecipeBinding>()
                .eq(MesRecipeBinding::getStepId, stepId)
                .eq(MesRecipeBinding::getEqpType, eqpType)
                .isNull(MesRecipeBinding::getEqpId)
                .eq(MesRecipeBinding::getEnabled, ENABLED)
                .last("LIMIT 1"));
    }

    private MesRecipeVersion resolveVersion(MesRecipeBinding binding) {
        if (binding.getRecipeVersionId() != null) {
            MesRecipeVersion pinned = mesRecipeVersionMapper.selectById(binding.getRecipeVersionId());
            if (pinned == null || !Objects.equals(pinned.getRecipeId(), binding.getRecipeId())) {
                return null;
            }
            return pinned;
        }
        return mesRecipeVersionMapper.selectOne(new LambdaQueryWrapper<MesRecipeVersion>()
                .eq(MesRecipeVersion::getRecipeId, binding.getRecipeId())
                .eq(MesRecipeVersion::getStatus, MesRecipeServiceImpl.STATUS_ACTIVE)
                .last("LIMIT 1"));
    }
}
