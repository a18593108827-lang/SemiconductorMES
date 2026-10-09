package com.mes.test.service;

import com.mes.common.PageResult;
import com.mes.test.dto.TestRecordCreateDTO;
import com.mes.test.dto.TestRecordQuery;
import com.mes.test.vo.TestRecordVO;

import java.util.Collection;
import java.util.List;

public interface MesTestRecordService {

    TestRecordVO create(TestRecordCreateDTO dto);

    PageResult<TestRecordVO> page(TestRecordQuery query);

    TestRecordVO get(Long id);

    void voidRecord(Long id, String reason);

    List<TestRecordVO> listByLot(Long lotId);

    List<TestRecordVO> listRecordsByLots(Collection<Long> lotIds, int capPerLot, int binCap);
}
