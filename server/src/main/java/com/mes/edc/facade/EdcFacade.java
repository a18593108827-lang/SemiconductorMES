package com.mes.edc.facade;

import com.mes.edc.vo.EdcGateResult;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.edc.vo.MesEdcPlanVO;

/**
 * 量测对外唯一门面：门禁判定 / 计划 / 本趟采集只读。
 * Track 只依赖本接口，禁止直查 mes_edc*。
 */
public interface EdcFacade {

    /**
     * 只读判定，不抛。
     * routeVersionId / sortNo 一期不参与 Plan 查找，供 TrackOut 透传。
     */
    EdcGateResult evaluateGate(Long lotId, Long routeVersionId, Integer sortNo, Long stepId);

    /** required && !clear 时抛 EDC_BLOCK_TRACK_OUT */
    void assertClearToTrackOut(Long lotId, Long routeVersionId, Integer sortNo, Long stepId);

    /** 本站启用计划；无则 null */
    MesEdcPlanVO getActivePlan(Long stepId);

    /** 同 visit 最新采集；无则 null */
    MesEdcCollectionVO getLatestCollection(Long lotId, Long trackInTxId);
}
