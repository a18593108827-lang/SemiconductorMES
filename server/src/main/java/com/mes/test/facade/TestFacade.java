package com.mes.test.facade;

import com.mes.test.vo.TestRecordVO;

import java.util.Collection;
import java.util.List;

public interface TestFacade {

    List<TestRecordVO> listRecordsByLots(Collection<Long> lotIds, int capPerLot);
}
