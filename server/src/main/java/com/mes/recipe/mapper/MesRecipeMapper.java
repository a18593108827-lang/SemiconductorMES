package com.mes.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.recipe.entity.MesRecipe;
import org.apache.ibatis.annotations.Mapper;

/** 配方主数据 Mapper */
@Mapper
public interface MesRecipeMapper extends BaseMapper<MesRecipe> {
}
