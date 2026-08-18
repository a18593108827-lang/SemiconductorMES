package com.mes.edc.service;

import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcParamCreateDTO;
import com.mes.edc.dto.MesEdcParamQuery;
import com.mes.edc.dto.MesEdcParamUpdateDTO;
import com.mes.edc.vo.MesEdcParamVO;

/** 量测特性：增删改查 + 启停（一期不做物理删，停用即可） */
public interface MesEdcParamService {

    PageResult<MesEdcParamVO> page(MesEdcParamQuery query);

    MesEdcParamVO get(Long id);

    MesEdcParamVO create(MesEdcParamCreateDTO dto);

    /** 改名称/单位/备注；编码锁死 */
    void update(Long id, MesEdcParamUpdateDTO dto);

    void updateEnabled(Long id, Integer enabled);
}
