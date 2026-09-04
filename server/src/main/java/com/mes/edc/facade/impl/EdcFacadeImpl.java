package com.mes.edc.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.BusinessException;
import com.mes.edc.entity.MesEdcPlan;
import com.mes.edc.facade.EdcFacade;
import com.mes.edc.mapper.MesEdcPlanMapper;
import com.mes.edc.service.MesEdcCollectionService;
import com.mes.edc.service.MesEdcPlanService;
import com.mes.edc.vo.EdcGateResult;
import com.mes.edc.vo.EdcSeriesPoint;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.edc.vo.MesEdcPlanVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 量测对外入口。
 * 别人只问一句：这批在这站能不能出。这里给答案，不改批次、不替他们完工。
 */
@Service
@RequiredArgsConstructor
public class EdcFacadeImpl implements EdcFacade {

    public static final String REASON_NONE = "NONE";
    public static final String REASON_NO_DATA = "NO_DATA";
    public static final String REASON_OOS = "OOS";
    public static final String REASON_GATE_DISABLED = "GATE_DISABLED";
    public static final String ERR_BLOCK = "EDC_BLOCK_TRACK_OUT";
    public static final String RESULT_PASS = "PASS";
    public static final String TX_TRACK_IN = "TRACK_IN";
    public static final String STATUS_PROCESSING = "processing";
    public static final int ENABLED = 1;
    public static final int REQUIRED = 1;

    private final MesEdcPlanMapper mesEdcPlanMapper;
    private final MesEdcPlanService mesEdcPlanService;
    private final MesEdcCollectionService mesEdcCollectionService;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;

    /** 出事时把量测卡控先关掉，计划不用删。默认开着。 */
    @Value("${mes.edc.gate-enabled:true}")
    private boolean gateEnabled;

    /**
     * 看这站要不要采、这趟采过没有、合格没有。只看，不拦。
     * 没要求就放行；没采或超规会告诉你不行。后面两个参数这期用不到。
     */
    @Override
    public EdcGateResult evaluateGate(Long lotId, Long routeVersionId, Integer sortNo, Long stepId) {
        MesEdcPlan plan = findEnabledPlan(stepId);
        if (plan == null || !Objects.equals(plan.getRequired(), REQUIRED)) {
            return result(false, true, REASON_NONE, "本站不要求量测", null, null);
        }
        if (!gateEnabled) {
            return result(true, true, REASON_GATE_DISABLED, "量测门禁已关闭（应急）", null, null);
        }
        Long trackInTxId = resolveTrackInTxId(lotId, stepId);
        if (trackInTxId == null) {
            return result(true, false, REASON_NO_DATA, "本趟尚未采集量测", null, null);
        }
        MesEdcCollectionVO col = mesEdcCollectionService.getLatest(lotId, trackInTxId);
        if (col == null) {
            return result(true, false, REASON_NO_DATA, "本趟尚未采集量测", null, trackInTxId);
        }
        if (!RESULT_PASS.equals(col.getResult())) {
            return result(true, false, REASON_OOS, "本趟量测不合格", col.getId(), trackInTxId);
        }
        return result(true, true, REASON_NONE, "本趟量测合格", col.getId(), trackInTxId);
    }

    /** 完工前最后问一次：这站要采但没合格，直接拦住。 */
    @Override
    public void assertClearToTrackOut(Long lotId, Long routeVersionId, Integer sortNo, Long stepId) {
        EdcGateResult gate = evaluateGate(lotId, routeVersionId, sortNo, stepId);
        if (gate.isRequired() && !gate.isClear()) {
            throw new BusinessException(ERR_BLOCK + ": " + gate.getMessage());
        }
    }

    /** 这站现在要采哪些、卡不卡。没配或停了就当没有。 */
    @Override
    public MesEdcPlanVO getActivePlan(Long stepId) {
        MesEdcPlan plan = findEnabledPlan(stepId);
        if (plan == null) {
            return null;
        }
        return mesEdcPlanService.get(plan.getId());
    }

    /** 这批这次进站后最近一次量测。没有就是没有。 */
    @Override
    public MesEdcCollectionVO getLatestCollection(Long lotId, Long trackInTxId) {
        if (lotId == null || trackInTxId == null) {
            return null;
        }
        return mesEdcCollectionService.getLatest(lotId, trackInTxId);
    }

    /** 给 SPC 拉某站某特性最近一串点。超规的也带，别把过程画好看了。机台空着就不过滤。 */
    @Override
    public List<EdcSeriesPoint> listSeries(Long paramId, Long stepId, Long eqpId,
                                           LocalDateTime from, LocalDateTime to, Integer limit) {
        return mesEdcCollectionService.listSeries(paramId, stepId, eqpId, from, to, limit);
    }

    /** 按单号把头和点一起拿回来。没有就空，不抛错，给采集后监听用。 */
    @Override
    public MesEdcCollectionVO getCollection(Long collectionId) {
        return mesEdcCollectionService.find(collectionId);
    }

    /** 找这站正在用的采集计划。一站只有一份。 */
    private MesEdcPlan findEnabledPlan(Long stepId) {
        if (stepId == null) {
            return null;
        }
        return mesEdcPlanMapper.selectOne(new LambdaQueryWrapper<MesEdcPlan>()
                .eq(MesEdcPlan::getStepId, stepId)
                .eq(MesEdcPlan::getEnabled, ENABLED)
                .last("LIMIT 1"));
    }

    /** 批次必须正在这站加工，才认这次开工。已经出站或换站了，不算这一趟。 */
    private Long resolveTrackInTxId(Long lotId, Long stepId) {
        if (lotId == null || stepId == null) {
            return null;
        }
        MesLot lot = mesLotMapper.selectById(lotId);
        if (lot == null || !STATUS_PROCESSING.equals(lot.getStatus())
                || !Objects.equals(lot.getCurrentStepId(), stepId)) {
            return null;
        }
        MesTxLog tx = mesTxLogMapper.selectOne(new LambdaQueryWrapper<MesTxLog>()
                .eq(MesTxLog::getLotId, lotId)
                .eq(MesTxLog::getTxType, TX_TRACK_IN)
                .eq(MesTxLog::getStepId, stepId)
                .orderByDesc(MesTxLog::getCreateTime)
                .orderByDesc(MesTxLog::getId)
                .last("LIMIT 1"));
        return tx == null ? null : tx.getId();
    }

    /** 把判定结果填进返回对象。 */
    private static EdcGateResult result(boolean required, boolean clear, String reasonCode, String message,
                                        Long collectionId, Long trackInTxId) {
        EdcGateResult vo = new EdcGateResult();
        vo.setRequired(required);
        vo.setClear(clear);
        vo.setReasonCode(reasonCode);
        vo.setMessage(message);
        vo.setCollectionId(collectionId);
        vo.setTrackInTxId(trackInTxId);
        return vo;
    }
}
