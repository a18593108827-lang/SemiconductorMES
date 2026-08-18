package com.mes.edc.service;

import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcSpecCreateDTO;
import com.mes.edc.dto.MesEdcSpecQuery;
import com.mes.edc.dto.MesEdcSpecUpdateDTO;
import com.mes.edc.vo.MesEdcSpecVO;

/** 量测规格：草稿 CRUD + 发布（同 param+product 只留一条 active） */
public interface MesEdcSpecService {

    PageResult<MesEdcSpecVO> page(MesEdcSpecQuery query);

    MesEdcSpecVO get(Long id);

    /** 新建草稿；同 param+product 已有草稿则拒绝 */
    MesEdcSpecVO create(MesEdcSpecCreateDTO dto);

    /** 仅草稿可改上下限 */
    void update(Long id, MesEdcSpecUpdateDTO dto);

    /** 发布：旧 active 变 obsolete，本版变 active */
    void publish(Long id);
}
