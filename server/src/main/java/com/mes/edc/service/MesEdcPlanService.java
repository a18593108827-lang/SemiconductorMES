package com.mes.edc.service;

import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcPlanCreateDTO;
import com.mes.edc.dto.MesEdcPlanItemsReplaceDTO;
import com.mes.edc.dto.MesEdcPlanQuery;
import com.mes.edc.dto.MesEdcPlanUpdateDTO;
import com.mes.edc.vo.MesEdcPlanVO;

/** 站计划：头 CRUD/启停 + 计划项整表替换 */
public interface MesEdcPlanService {

    PageResult<MesEdcPlanVO> page(MesEdcPlanQuery query);

    /** 详情带 items */
    MesEdcPlanVO get(Long id);

    MesEdcPlanVO create(MesEdcPlanCreateDTO dto);

    void update(Long id, MesEdcPlanUpdateDTO dto);

    void updateEnabled(Long id, Integer enabled);

    /** 整表替换计划项；开了门禁时不允许空列表 */
    void replaceItems(Long id, MesEdcPlanItemsReplaceDTO dto);
}
