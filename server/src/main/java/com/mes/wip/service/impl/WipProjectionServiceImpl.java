package com.mes.wip.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mes.lot.entity.MesLot;
import com.mes.wip.entity.MesWipLot;
import com.mes.wip.mapper.MesWipLotMapper;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WipProjectionServiceImpl implements WipProjectionService {

    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_HELD = "held";

    private static final Set<String> WIP_STATUSES = Set.of(STATUS_WAIT, STATUS_PROCESSING, STATUS_HELD);

    private final MesWipLotMapper mesWipLotMapper;

    @Override
    public void syncFromLot(MesLot lot) {
        if (lot == null || lot.getId() == null) {
            return;
        }
        if (!WIP_STATUSES.contains(lot.getStatus())) {
            remove(lot.getId());
            return;
        }
        MesWipLot existing = mesWipLotMapper.selectById(lot.getId());
        if (existing == null) {
            mesWipLotMapper.insert(toRow(lot));
            return;
        }
        // updateById 跳过 null，Abort/Out 腾机后 current_eqp_id 清不掉；改用 Wrapper 显式写入
        LocalDateTime now = lot.getUpdateTime() != null ? lot.getUpdateTime() : LocalDateTime.now();
        mesWipLotMapper.update(null, new LambdaUpdateWrapper<MesWipLot>()
                .eq(MesWipLot::getLotId, lot.getId())
                .set(MesWipLot::getLotNo, lot.getLotNo())
                .set(MesWipLot::getProductCode, lot.getProductCode())
                .set(MesWipLot::getQty, lot.getQty())
                .set(MesWipLot::getPriority, lot.getPriority())
                .set(MesWipLot::getHotFlag, lot.getHotFlag() == null ? 0 : lot.getHotFlag())
                .set(MesWipLot::getCustomerLot, lot.getCustomerLot())
                .set(MesWipLot::getStatus, lot.getStatus())
                .set(MesWipLot::getCurrentSortNo, lot.getCurrentSortNo())
                .set(MesWipLot::getCurrentStepId, lot.getCurrentStepId())
                .set(MesWipLot::getCurrentEqpId, lot.getCurrentEqpId())
                .set(MesWipLot::getRouteId, lot.getRouteId())
                .set(MesWipLot::getRouteVersionId, lot.getRouteVersionId())
                .set(MesWipLot::getUpdateTime, now));
    }

    @Override
    public void remove(Long lotId) {
        if (lotId != null) {
            mesWipLotMapper.deleteById(lotId);
        }
    }

    private static MesWipLot toRow(MesLot lot) {
        MesWipLot row = new MesWipLot();
        row.setLotId(lot.getId());
        row.setLotNo(lot.getLotNo());
        row.setProductCode(lot.getProductCode());
        row.setQty(lot.getQty());
        row.setPriority(lot.getPriority());
        row.setHotFlag(lot.getHotFlag() == null ? 0 : lot.getHotFlag());
        row.setCustomerLot(lot.getCustomerLot());
        row.setStatus(lot.getStatus());
        row.setCurrentSortNo(lot.getCurrentSortNo());
        row.setCurrentStepId(lot.getCurrentStepId());
        row.setCurrentEqpId(lot.getCurrentEqpId());
        row.setRouteId(lot.getRouteId());
        row.setRouteVersionId(lot.getRouteVersionId());
        row.setUpdateTime(lot.getUpdateTime() != null ? lot.getUpdateTime() : LocalDateTime.now());
        return row;
    }
}
