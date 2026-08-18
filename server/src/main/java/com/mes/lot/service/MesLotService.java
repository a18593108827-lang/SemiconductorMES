package com.mes.lot.service;

import com.mes.common.PageResult;
import com.mes.lot.dto.MesLotCreateDTO;
import com.mes.lot.dto.MesLotQuery;
import com.mes.lot.dto.MesLotUpdateDTO;
import com.mes.lot.vo.MesLotCreateResultVO;
import com.mes.lot.vo.MesLotGenealogyNodeVO;
import com.mes.lot.vo.MesLotVO;

/** 批次服务 */
public interface MesLotService {

    /** 分页列表 */
    PageResult<MesLotVO> page(MesLotQuery query);

    /** 新建批次；lotNo 空则自动生成 LOT-yyyyMMdd-流水 */
    MesLotCreateResultVO create(MesLotCreateDTO dto);

    /** 详情（含路线摘要；已放行含步骤） */
    MesLotVO get(Long id);

    /** 改属性；已放行不可改路线版本 */
    void update(Long id, MesLotUpdateDTO dto);

    /** 放行：绑定当时 active 的 route_version_id */
    void release(Long id);

    /**
     * 谱系树。
     * @param direction up|down|both
     * @param depth 最大深度，默认 5
     */
    MesLotGenealogyNodeVO genealogy(Long lotId, String direction, Integer depth);
}
