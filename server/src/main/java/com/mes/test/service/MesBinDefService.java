package com.mes.test.service;

import com.mes.test.dto.TestBinQuery;
import com.mes.test.dto.TestBinSaveDTO;
import com.mes.test.vo.TestBinDefVO;

import java.util.List;

public interface MesBinDefService {

    List<TestBinDefVO> list(TestBinQuery query);

    TestBinDefVO create(TestBinSaveDTO dto);

    void update(Long id, TestBinSaveDTO dto);
}
