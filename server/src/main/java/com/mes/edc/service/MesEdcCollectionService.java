package com.mes.edc.service;

import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcCollectionCreateDTO;
import com.mes.edc.dto.MesEdcCollectionQuery;
import com.mes.edc.vo.MesEdcCollectionVO;

/** 手录采集：提交判定 + 按 visit 查询 */
public interface MesEdcCollectionService {

    PageResult<MesEdcCollectionVO> page(MesEdcCollectionQuery query);

    MesEdcCollectionVO get(Long id);

    /** 同 visit 最新一条；没有则 null */
    MesEdcCollectionVO getLatest(Long lotId, Long trackInTxId);

    MesEdcCollectionVO submit(MesEdcCollectionCreateDTO dto);
}
