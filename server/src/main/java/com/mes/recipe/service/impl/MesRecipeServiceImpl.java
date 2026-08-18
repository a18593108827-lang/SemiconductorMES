package com.mes.recipe.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.dto.MesRecipeVersionCreateDTO;
import com.mes.recipe.dto.MesRecipeVersionUpdateDTO;
import com.mes.recipe.entity.MesRecipe;
import com.mes.recipe.entity.MesRecipeVersion;
import com.mes.recipe.mapper.MesRecipeMapper;
import com.mes.recipe.mapper.MesRecipeVersionMapper;
import com.mes.recipe.service.MesRecipeService;
import com.mes.recipe.vo.MesRecipeVO;
import com.mes.recipe.vo.MesRecipeVersionDetailVO;
import com.mes.recipe.vo.MesRecipeVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 配方主数据 CRUD / 启停；版本草稿 / 发布 */
@Service
@RequiredArgsConstructor
public class MesRecipeServiceImpl implements MesRecipeService {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_OBSOLETE = "obsolete";

    private final MesRecipeMapper mesRecipeMapper;
    private final MesRecipeVersionMapper mesRecipeVersionMapper;

    @Override
    public PageResult<MesRecipeVO> page(MesRecipeQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesRecipe> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesRecipe::getRecipeCode, kw).or().like(MesRecipe::getRecipeName, kw));
        }
        if (query.getEnabled() != null) {
            qw.eq(MesRecipe::getEnabled, query.getEnabled());
        }
        qw.orderByAsc(MesRecipe::getRecipeCode);

        Page<MesRecipe> result = mesRecipeMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesRecipe> rows = result.getRecords();
        Map<Long, MesRecipeVersion> activeMap = loadActiveMap(rows);
        List<MesRecipeVO> records = new ArrayList<>(rows.size());
        for (MesRecipe row : rows) {
            records.add(toVo(row, activeMap.get(row.getId())));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesRecipeVO get(Long id) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");
        MesRecipeVersion active = mesRecipeVersionMapper.selectOne(new LambdaQueryWrapper<MesRecipeVersion>()
                .eq(MesRecipeVersion::getRecipeId, id)
                .eq(MesRecipeVersion::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        return toVo(row, active);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRecipeVO create(MesRecipeCreateDTO dto) {
        String code = dto.getRecipeCode().trim();
        Long count = mesRecipeMapper.selectCount(new LambdaQueryWrapper<MesRecipe>().eq(MesRecipe::getRecipeCode, code));
        AssertUtil.isTrue(count == 0, "配方编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesRecipe row = new MesRecipe();
        row.setRecipeCode(code);
        row.setRecipeName(dto.getRecipeName().trim());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setEnabled(ENABLED);
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesRecipeMapper.insert(row);
        return toVo(row, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesRecipeUpdateDTO dto) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");

        row.setRecipeName(dto.getRecipeName().trim());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesRecipeMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        MesRecipe row = mesRecipeMapper.selectById(id);
        AssertUtil.notNull(row, "配方不存在");
        AssertUtil.isTrue(enabled != null && (enabled == ENABLED || enabled == DISABLED), "启停只能为0或1");

        row.setEnabled(enabled);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesRecipeMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    public List<MesRecipeVersionVO> listVersions(Long recipeId) {
        AssertUtil.notNull(mesRecipeMapper.selectById(recipeId), "配方不存在");
        List<MesRecipeVersion> versions = mesRecipeVersionMapper.selectList(new LambdaQueryWrapper<MesRecipeVersion>()
                .eq(MesRecipeVersion::getRecipeId, recipeId)
                .orderByDesc(MesRecipeVersion::getVersionNo));
        List<MesRecipeVersionVO> list = new ArrayList<>(versions.size());
        for (MesRecipeVersion v : versions) {
            list.add(toVersionVo(v));
        }
        return list;
    }

    @Override
    public MesRecipeVersionDetailVO getVersion(Long versionId) {
        MesRecipeVersion version = mesRecipeVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        MesRecipe recipe = mesRecipeMapper.selectById(version.getRecipeId());
        AssertUtil.notNull(recipe, "配方不存在");
        return toVersionDetailVo(version, recipe);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDraft(Long recipeId, MesRecipeVersionCreateDTO dto) {
        MesRecipe recipe = mesRecipeMapper.selectById(recipeId);
        AssertUtil.notNull(recipe, "配方不存在");

        Long draftCount = mesRecipeVersionMapper.selectCount(new LambdaQueryWrapper<MesRecipeVersion>()
                .eq(MesRecipeVersion::getRecipeId, recipeId)
                .eq(MesRecipeVersion::getStatus, STATUS_DRAFT));
        AssertUtil.isTrue(draftCount == 0, "已有草稿版本，请先发布或继续编辑现有草稿");

        Integer maxNo = mesRecipeVersionMapper.selectList(new LambdaQueryWrapper<MesRecipeVersion>()
                        .eq(MesRecipeVersion::getRecipeId, recipeId)
                        .orderByDesc(MesRecipeVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream()
                .map(MesRecipeVersion::getVersionNo)
                .findFirst()
                .orElse(0);

        long userId = StpUtil.getLoginIdAsLong();
        MesRecipeVersion draft = new MesRecipeVersion();
        draft.setRecipeId(recipeId);
        draft.setVersionNo(maxNo + 1);
        draft.setStatus(STATUS_DRAFT);
        draft.setCreateBy(userId);
        draft.setUpdateBy(userId);

        if (dto != null && dto.getFromVersionId() != null) {
            MesRecipeVersion from = mesRecipeVersionMapper.selectById(dto.getFromVersionId());
            AssertUtil.notNull(from, "源版本不存在");
            AssertUtil.isTrue(Objects.equals(from.getRecipeId(), recipeId), "源版本不属于该配方");
            draft.setBodyJson(from.getBodyJson());
            draft.setBodyObjectKey(from.getBodyObjectKey());
            draft.setRemark(StringUtils.hasText(dto.getRemark())
                    ? dto.getRemark().trim()
                    : "基于 v" + from.getVersionNo() + " 升版");
        } else {
            draft.setRemark(dto != null ? blankToNull(dto.getRemark()) : null);
        }

        mesRecipeVersionMapper.insert(draft);
        return draft.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long versionId, MesRecipeVersionUpdateDTO dto) {
        MesRecipeVersion version = mesRecipeVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿版本可编辑");

        version.setBodyJson(blankToNull(dto.getBodyJson()));
        version.setBodyObjectKey(blankToNull(dto.getBodyObjectKey()));
        version.setRemark(blankToNull(dto.getRemark()));
        version.setUpdateBy(StpUtil.getLoginIdAsLong());
        mesRecipeVersionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long versionId) {
        MesRecipeVersion version = mesRecipeVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿可发布");

        mesRecipeVersionMapper.update(null, new LambdaUpdateWrapper<MesRecipeVersion>()
                .eq(MesRecipeVersion::getRecipeId, version.getRecipeId())
                .eq(MesRecipeVersion::getStatus, STATUS_ACTIVE)
                .set(MesRecipeVersion::getStatus, STATUS_OBSOLETE));

        version.setStatus(STATUS_ACTIVE);
        version.setPublishedAt(LocalDateTime.now());
        version.setUpdateBy(StpUtil.getLoginIdAsLong());
        mesRecipeVersionMapper.updateById(version);
    }

    private Map<Long, MesRecipeVersion> loadActiveMap(List<MesRecipe> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(MesRecipe::getId).toList();
        return mesRecipeVersionMapper.selectList(new LambdaQueryWrapper<MesRecipeVersion>()
                        .in(MesRecipeVersion::getRecipeId, ids)
                        .eq(MesRecipeVersion::getStatus, STATUS_ACTIVE))
                .stream()
                .collect(Collectors.toMap(MesRecipeVersion::getRecipeId, v -> v, (a, b) -> a));
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private MesRecipeVO toVo(MesRecipe row, MesRecipeVersion active) {
        MesRecipeVO vo = new MesRecipeVO();
        vo.setId(row.getId());
        vo.setRecipeCode(row.getRecipeCode());
        vo.setRecipeName(row.getRecipeName());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        if (active != null) {
            vo.setActiveVersionId(active.getId());
            vo.setActiveVersionNo(active.getVersionNo());
        }
        return vo;
    }

    private static MesRecipeVersionVO toVersionVo(MesRecipeVersion v) {
        MesRecipeVersionVO vo = new MesRecipeVersionVO();
        vo.setId(v.getId());
        vo.setRecipeId(v.getRecipeId());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setRemark(v.getRemark());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setCreateTime(v.getCreateTime());
        vo.setUpdateTime(v.getUpdateTime());
        return vo;
    }

    private static MesRecipeVersionDetailVO toVersionDetailVo(MesRecipeVersion v, MesRecipe recipe) {
        MesRecipeVersionDetailVO vo = new MesRecipeVersionDetailVO();
        vo.setId(v.getId());
        vo.setRecipeId(v.getRecipeId());
        vo.setRecipeCode(recipe.getRecipeCode());
        vo.setRecipeName(recipe.getRecipeName());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setBodyJson(v.getBodyJson());
        vo.setBodyObjectKey(v.getBodyObjectKey());
        vo.setRemark(v.getRemark());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setCreateTime(v.getCreateTime());
        vo.setUpdateTime(v.getUpdateTime());
        return vo;
    }
}
