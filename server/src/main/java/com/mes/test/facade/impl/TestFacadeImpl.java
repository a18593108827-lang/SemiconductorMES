package com.mes.test.facade.impl;

import com.mes.test.facade.TestFacade;
import com.mes.test.service.MesTestRecordService;
import com.mes.test.vo.TestRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestFacadeImpl implements TestFacade {

    private final MesTestRecordService mesTestRecordService;

    /** 按批取最近记录及其 Bin 汇总，供客诉包只读调用 */
    @Override
    public List<TestRecordVO> listRecordsByLots(Collection<Long> lotIds, int capPerLot) {
        return mesTestRecordService.listRecordsByLots(lotIds, capPerLot);
    }
}
