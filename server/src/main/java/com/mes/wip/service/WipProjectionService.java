package com.mes.wip.service;

import com.mes.lot.entity.MesLot;

/** 在制投影同步（由 Track / Lot 在同事务调用） */
public interface WipProjectionService {

    /** 在制则 upsert；非在制则删除 */
    void syncFromLot(MesLot lot);

    void remove(Long lotId);
}
