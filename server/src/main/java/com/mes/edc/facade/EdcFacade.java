package com.mes.edc.facade;

import com.mes.edc.vo.EdcGateResult;
import com.mes.edc.vo.EdcSeriesPoint;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.edc.vo.MesEdcPlanVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 量测对外唯一门面：门禁判定 / 计划 / 采集只读。
 * Track / SPC 只依赖本接口，禁止直查 mes_edc*。
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

    /**
     * 给 SPC 拉某站某特性最近一串点。超规的也带，别把过程画好看了。
     * 机台空着就不过滤；条数默认 100、最多 500。时间从早到晚。
     */
    List<EdcSeriesPoint> listSeries(Long paramId, Long stepId, Long eqpId,
                                    LocalDateTime from, LocalDateTime to, Integer limit);

    /** 按单号把头和点一起拿回来。没有就空，不抛错，给采集后监听用。 */
    MesEdcCollectionVO getCollection(Long collectionId);
}
