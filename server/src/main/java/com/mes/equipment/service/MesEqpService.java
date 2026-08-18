package com.mes.equipment.service;

import com.mes.common.PageResult;
import com.mes.equipment.dto.MesEqpCreateDTO;
import com.mes.equipment.dto.MesEqpQuery;
import com.mes.equipment.dto.MesEqpUpdateDTO;
import com.mes.equipment.vo.MesEqpOptionVO;
import com.mes.equipment.vo.MesEqpVO;

import java.util.List;

public interface MesEqpService {

    PageResult<MesEqpVO> page(MesEqpQuery query);

    MesEqpVO get(Long id);

    /** 启用且可开工设备，供 TrackIn 等下拉；eqpType 可选过滤 */
    List<MesEqpOptionVO> listOptions(String eqpType);

    MesEqpVO create(MesEqpCreateDTO dto);

    void update(Long id, MesEqpUpdateDTO dto);

    void updateEnabled(Long id, Integer enabled);

    /** 改业务态 */
    void updateStatus(Long id, String status);

    /**
     * Track 钩子：设备存在、启用、状态可开工（idle/running）。
     * eqpId 为空则跳过（一期 TrackIn 设备可选）。
     */
    void assertUsable(Long eqpId);
}
