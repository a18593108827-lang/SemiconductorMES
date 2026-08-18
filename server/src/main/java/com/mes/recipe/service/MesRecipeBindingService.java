package com.mes.recipe.service;

import com.mes.common.PageResult;
import com.mes.recipe.dto.MesRecipeBindingCreateDTO;
import com.mes.recipe.dto.MesRecipeBindingQuery;
import com.mes.recipe.vo.MesRecipeBindingVO;

/** Step×Eqp 配方绑定 */
public interface MesRecipeBindingService {

    PageResult<MesRecipeBindingVO> page(MesRecipeBindingQuery query);

    MesRecipeBindingVO get(Long id);

    MesRecipeBindingVO create(MesRecipeBindingCreateDTO dto);

    void delete(Long id);
}
