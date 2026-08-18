package com.mes.route.service;

import com.mes.common.PageResult;
import com.mes.route.dto.MesStepCreateDTO;
import com.mes.route.dto.MesStepQuery;
import com.mes.route.dto.MesStepUpdateDTO;
import com.mes.route.vo.MesStepVO;

/** 工序服务 */
public interface MesStepService {

    /** 分页查询工序 */
    PageResult<MesStepVO> page(MesStepQuery query);

    /** 新增工序 */
    void create(MesStepCreateDTO dto);

    /** 编辑工序 */
    void update(Long id, MesStepUpdateDTO dto);
}
