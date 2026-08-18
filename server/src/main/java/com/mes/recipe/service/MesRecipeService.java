package com.mes.recipe.service;

import com.mes.common.PageResult;
import com.mes.recipe.dto.MesRecipeCreateDTO;
import com.mes.recipe.dto.MesRecipeQuery;
import com.mes.recipe.dto.MesRecipeUpdateDTO;
import com.mes.recipe.dto.MesRecipeVersionCreateDTO;
import com.mes.recipe.dto.MesRecipeVersionUpdateDTO;
import com.mes.recipe.vo.MesRecipeVO;
import com.mes.recipe.vo.MesRecipeVersionDetailVO;
import com.mes.recipe.vo.MesRecipeVersionVO;

import java.util.List;

/** 配方主数据：CRUD / 启停；版本：草稿 / 发布 */
public interface MesRecipeService {

    PageResult<MesRecipeVO> page(MesRecipeQuery query);

    MesRecipeVO get(Long id);

    MesRecipeVO create(MesRecipeCreateDTO dto);

    /** 改名称/备注；不改 recipeCode */
    void update(Long id, MesRecipeUpdateDTO dto);

    void updateEnabled(Long id, Integer enabled);

    List<MesRecipeVersionVO> listVersions(Long recipeId);

    MesRecipeVersionDetailVO getVersion(Long versionId);

    /** 新建草稿；同配方仅允许一个 draft */
    Long createDraft(Long recipeId, MesRecipeVersionCreateDTO dto);

    /** 仅草稿可改 body / remark */
    void updateDraft(Long versionId, MesRecipeVersionUpdateDTO dto);

    /** 发布：旧 active → obsolete，本版 → active */
    void publish(Long versionId);
}
