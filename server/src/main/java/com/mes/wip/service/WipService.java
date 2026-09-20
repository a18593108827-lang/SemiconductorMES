package com.mes.wip.service;

import com.mes.common.PageResult;
import com.mes.wip.dto.MesWipQuery;
import com.mes.wip.vo.MesWipStepSummaryVO;
import com.mes.wip.vo.MesWipVO;

import java.util.List;

public interface WipService {

    PageResult<MesWipVO> page(MesWipQuery query);

    /**
     * 在制总数：与 page 默认口径一致（wait / processing / held），只 COUNT，不组装列表。
     */
    long count();

    List<MesWipStepSummaryVO> summaryByStep();
}
