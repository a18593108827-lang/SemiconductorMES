package com.mes.wip.service;

import com.mes.common.PageResult;
import com.mes.wip.dto.MesWipQuery;
import com.mes.wip.vo.MesWipStepSummaryVO;
import com.mes.wip.vo.MesWipVO;

import java.util.List;

public interface WipService {

    PageResult<MesWipVO> page(MesWipQuery query);

    List<MesWipStepSummaryVO> summaryByStep();
}
