package com.mes.recipe.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.recipe.dto.MesRecipeBindingCreateDTO;
import com.mes.recipe.dto.MesRecipeBindingQuery;
import com.mes.recipe.entity.MesRecipe;
import com.mes.recipe.entity.MesRecipeBinding;
import com.mes.recipe.entity.MesRecipeVersion;
import com.mes.recipe.mapper.MesRecipeBindingMapper;
import com.mes.recipe.mapper.MesRecipeMapper;
import com.mes.recipe.mapper.MesRecipeVersionMapper;
import com.mes.recipe.service.MesRecipeBindingService;
import com.mes.recipe.vo.MesRecipeBindingVO;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Step×Eqp 配方绑定 */
@Service
@RequiredArgsConstructor
public class MesRecipeBindingServiceImpl implements MesRecipeBindingService {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    private final MesRecipeBindingMapper mesRecipeBindingMapper;
    private final MesRecipeMapper mesRecipeMapper;
    private final MesRecipeVersionMapper mesRecipeVersionMapper;
    private final MesStepMapper mesStepMapper;
    private final MesEqpMapper mesEqpMapper;

    @Override
    public PageResult<MesRecipeBindingVO> page(MesRecipeBindingQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesRecipeBinding> qw = new LambdaQueryWrapper<>();
        if (query.getStepId() != null) {
            qw.eq(MesRecipeBinding::getStepId, query.getStepId());
        }
        if (query.getRecipeId() != null) {
            qw.eq(MesRecipeBinding::getRecipeId, query.getRecipeId());
        }
        if (query.getEqpId() != null) {
            qw.eq(MesRecipeBinding::getEqpId, query.getEqpId());
        }
        if (StringUtils.hasText(query.getEqpType())) {
            qw.eq(MesRecipeBinding::getEqpType, query.getEqpType().trim());
        }
        if (query.getEnabled() != null) {
            qw.eq(MesRecipeBinding::getEnabled, query.getEnabled());
        }
        qw.orderByDesc(MesRecipeBinding::getUpdateTime);

        Page<MesRecipeBinding> result = mesRecipeBindingMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesRecipeBindingVO> records = toVoList(result.getRecords());
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesRecipeBindingVO get(Long id) {
        MesRecipeBinding row = mesRecipeBindingMapper.selectById(id);
        AssertUtil.notNull(row, "绑定不存在");
        List<MesRecipeBindingVO> list = toVoList(List.of(row));
        return list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRecipeBindingVO create(MesRecipeBindingCreateDTO dto) {
        boolean hasEqp = dto.getEqpId() != null;
        boolean hasType = StringUtils.hasText(dto.getEqpType());
        AssertUtil.isTrue(hasEqp || hasType, "设备ID与设备类型至少填一个");

        MesStep step = mesStepMapper.selectById(dto.getStepId());
        AssertUtil.notNull(step, "工序不存在");

        MesRecipe recipe = mesRecipeMapper.selectById(dto.getRecipeId());
        AssertUtil.notNull(recipe, "配方不存在");

        if (hasEqp) {
            MesEqp eqp = mesEqpMapper.selectById(dto.getEqpId());
            AssertUtil.notNull(eqp, "设备不存在");
        }

        Long versionId = dto.getRecipeVersionId();
        if (versionId != null) {
            MesRecipeVersion ver = mesRecipeVersionMapper.selectById(versionId);
            AssertUtil.notNull(ver, "配方版本不存在");
            AssertUtil.isTrue(Objects.equals(ver.getRecipeId(), dto.getRecipeId()), "版本不属于该配方");
        }

        Integer enabled = dto.getEnabled() == null ? ENABLED : dto.getEnabled();
        AssertUtil.isTrue(enabled == ENABLED || enabled == DISABLED, "启停只能为0或1");

        long userId = StpUtil.getLoginIdAsLong();
        MesRecipeBinding row = new MesRecipeBinding();
        row.setStepId(dto.getStepId());
        row.setEqpId(dto.getEqpId());
        row.setEqpType(hasType ? dto.getEqpType().trim() : null);
        row.setRecipeId(dto.getRecipeId());
        row.setRecipeVersionId(versionId);
        row.setEnabled(enabled);
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesRecipeBindingMapper.insert(row);
        return get(row.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MesRecipeBinding row = mesRecipeBindingMapper.selectById(id);
        AssertUtil.notNull(row, "绑定不存在");
        mesRecipeBindingMapper.deleteById(id);
    }

    private List<MesRecipeBindingVO> toVoList(List<MesRecipeBinding> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> stepIds = new HashSet<>();
        Set<Long> recipeIds = new HashSet<>();
        Set<Long> eqpIds = new HashSet<>();
        Set<Long> versionIds = new HashSet<>();
        for (MesRecipeBinding row : rows) {
            stepIds.add(row.getStepId());
            recipeIds.add(row.getRecipeId());
            if (row.getEqpId() != null) {
                eqpIds.add(row.getEqpId());
            }
            if (row.getRecipeVersionId() != null) {
                versionIds.add(row.getRecipeVersionId());
            }
        }

        Map<Long, MesStep> stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(HashMap::new, (m, s) -> m.put(s.getId(), s), HashMap::putAll);
        Map<Long, MesRecipe> recipeMap = mesRecipeMapper.selectBatchIds(recipeIds).stream()
                .collect(HashMap::new, (m, r) -> m.put(r.getId(), r), HashMap::putAll);
        Map<Long, MesEqp> eqpMap = eqpIds.isEmpty() ? Map.of()
                : mesEqpMapper.selectBatchIds(eqpIds).stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getId(), e), HashMap::putAll);
        Map<Long, MesRecipeVersion> versionMap = versionIds.isEmpty() ? Map.of()
                : mesRecipeVersionMapper.selectBatchIds(versionIds).stream()
                .collect(HashMap::new, (m, v) -> m.put(v.getId(), v), HashMap::putAll);

        List<MesRecipeBindingVO> list = new ArrayList<>(rows.size());
        for (MesRecipeBinding row : rows) {
            MesRecipeBindingVO vo = new MesRecipeBindingVO();
            vo.setId(row.getId());
            vo.setStepId(row.getStepId());
            MesStep step = stepMap.get(row.getStepId());
            if (step != null) {
                vo.setStepCode(step.getStepCode());
                vo.setStepName(step.getStepName());
            }
            vo.setEqpId(row.getEqpId());
            if (row.getEqpId() != null) {
                MesEqp eqp = eqpMap.get(row.getEqpId());
                if (eqp != null) {
                    vo.setEqpCode(eqp.getEqpCode());
                }
            }
            vo.setEqpType(row.getEqpType());
            vo.setRecipeId(row.getRecipeId());
            MesRecipe recipe = recipeMap.get(row.getRecipeId());
            if (recipe != null) {
                vo.setRecipeCode(recipe.getRecipeCode());
                vo.setRecipeName(recipe.getRecipeName());
            }
            vo.setRecipeVersionId(row.getRecipeVersionId());
            if (row.getRecipeVersionId() != null) {
                MesRecipeVersion ver = versionMap.get(row.getRecipeVersionId());
                if (ver != null) {
                    vo.setRecipeVersionNo(ver.getVersionNo());
                }
            }
            vo.setEnabled(row.getEnabled());
            vo.setCreateTime(row.getCreateTime());
            vo.setUpdateTime(row.getUpdateTime());
            list.add(vo);
        }
        return list;
    }
}
