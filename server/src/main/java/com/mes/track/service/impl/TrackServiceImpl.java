package com.mes.track.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.dispatch.service.DispatchService;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.equipment.service.MesEqpService;
import com.mes.hold.service.FutureHoldService;
import com.mes.hold.service.HoldService;
import com.mes.hold.service.impl.FutureHoldServiceImpl;
import com.mes.edc.facade.EdcFacade;
import com.mes.edc.vo.EdcGateResult;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryTxVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.entity.MesLotGenealogy;
import com.mes.lot.mapper.MesLotGenealogyMapper;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.lot.vo.MesLotStepVO;
import com.mes.recipe.facade.RecipeFacade;
import com.mes.recipe.vo.RecipeResolveVO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.route.support.RouteEdgeResolver;
import com.mes.route.support.RouteEdgeTypes;
import com.mes.route.support.RouteTrackOutDecision;
import com.mes.route.support.StepEqpTypeGuard;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.dto.TrackSplitChildDTO;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import com.mes.track.service.TrackService;
import com.mes.track.support.OffFlowCountStore;
import com.mes.track.support.ProcessTimeSupport;
import com.mes.track.support.QueueTimeSupport;
import com.mes.track.support.ReworkCountStore;
import com.mes.track.vo.TrackAbortReasonVO;
import com.mes.track.vo.TrackBonusReasonVO;
import com.mes.track.vo.TrackBonusResultVO;
import com.mes.track.vo.TrackBranchOptionVO;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackEdcVO;
import com.mes.track.vo.TrackMergeCandidateVO;
import com.mes.track.vo.TrackMergeResultVO;
import com.mes.track.vo.TrackOffFlowOptionVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackReworkOptionVO;
import com.mes.track.vo.TrackScrapReasonVO;
import com.mes.track.vo.TrackScrapResultVO;
import com.mes.track.vo.TrackSkipOptionVO;
import com.mes.track.vo.TrackSplitResultVO;
import com.mes.track.vo.TrackTxnResultVO;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Track 执行引擎：Release / TrackIn / TrackOut（Out 内含自动进站，不单独暴露 Move）
 */
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    public static final String STATUS_CREATED = "created";
    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_SCRAPPED = "scrapped";
    public static final String STATUS_MERGED = "merged"; // 合批终态，不可再 Track
    public static final String ROUTE_ACTIVE = "active";

    public static final String TX_RELEASE = "RELEASE";
    public static final String TX_TRACK_IN = "TRACK_IN";
    public static final String TX_TRACK_OUT = "TRACK_OUT";
    public static final String TX_REWORK = "REWORK";
    public static final String TX_SKIP = "SKIP";
    public static final String TX_OFF_FLOW = "OFF_FLOW";
    public static final String TX_OFF_FLOW_RESUME = "OFF_FLOW_RESUME";
    public static final String TX_SPLIT = "SPLIT";
    public static final String TX_MERGE = "MERGE"; // 合批事务类型
    public static final String TX_SCRAP = "SCRAP";
    public static final String TX_BONUS = "BONUS";
    /** 加工中止履历类型 */
    public static final String TX_ABORT = "ABORT";
    /** 独立移站履历类型（没加工，只搬家） */
    public static final String TX_MOVE = "MOVE";

    public static final String ERR_ABORT_REASON_REQUIRED = "ABORT_REASON_REQUIRED";
    public static final String ERR_ABORT_REASON_INVALID = "ABORT_REASON_INVALID";
    public static final String ERR_ABORT_REMARK_REQUIRED = "ABORT_REMARK_REQUIRED";

    public static final String ERR_MOVE_NOT_WAIT = "MOVE_NOT_WAIT";
    public static final String ERR_MOVE_NO_NEXT = "MOVE_NO_NEXT";
    public static final String ERR_MOVE_TARGET_NOT_NEXT = "MOVE_TARGET_NOT_NEXT";
    public static final String ERR_MOVE_DIRTY_EQP = "MOVE_DIRTY_EQP";
    public static final String ERR_MOVE_DIRTY_PROCESS_TIME = "MOVE_DIRTY_PROCESS_TIME";
    public static final String ERR_MOVE_OFF_FLOW = "MOVE_OFF_FLOW";

    /** P0 Scrap 原因码白名单 */
    private static final List<TrackScrapReasonVO> SCRAP_REASON_CODES = List.of(
            new TrackScrapReasonVO("BREAKAGE", "破片/物理损坏"),
            new TrackScrapReasonVO("PROCESS_FAIL", "工艺失败"),
            new TrackScrapReasonVO("EQP_DAMAGE", "设备致损"),
            new TrackScrapReasonVO("CONTAMINATION", "污染"),
            new TrackScrapReasonVO("METROLOGY_FAIL", "量测不合格"),
            new TrackScrapReasonVO("OTHER", "其他")
    );

    /** P0 Bonus 原因码白名单（与 Scrap 隔离） */
    private static final List<TrackBonusReasonVO> BONUS_REASON_CODES = List.of(
            new TrackBonusReasonVO("CYCLE_COUNT", "盘点差异"),
            new TrackBonusReasonVO("RECEIPT_CORR", "收货/点片修正"),
            new TrackBonusReasonVO("METROLOGY_ADJ", "计量/点料修正"),
            new TrackBonusReasonVO("SYSTEM_CORR", "系统录入错误纠正"),
            new TrackBonusReasonVO("OTHER", "其他")
    );

    /** P0 Abort 原因码白名单（加工中止专用，别跟报废混） */
    private static final List<TrackAbortReasonVO> ABORT_REASON_CODES = List.of(
            new TrackAbortReasonVO("EQP_ABORT", "设备中止/报警"),
            new TrackAbortReasonVO("EQP_DOWN", "设备宕机/不可用"),
            new TrackAbortReasonVO("RECIPE_ERROR", "配方/参数错误"),
            new TrackAbortReasonVO("OPERATOR", "人为误操作/主动中止"),
            new TrackAbortReasonVO("PROCESS_ISSUE", "工艺异常（未到报废）"),
            new TrackAbortReasonVO("OTHER", "其他")
    );

    private final MesLotMapper mesLotMapper;
    private final MesLotGenealogyMapper mesLotGenealogyMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final WipProjectionService wipProjectionService;
    private final HoldService holdService;
    private final FutureHoldService futureHoldService;
    private final MesEqpService mesEqpService;
    private final MesEqpMapper mesEqpMapper;
    private final DispatchService dispatchService;
    private final RecipeFacade recipeFacade;
    private final EdcFacade edcFacade;
    private final HistoryFacade historyFacade;
    private final RouteEdgeResolver routeEdgeResolver;
    private final ReworkCountStore reworkCountStore;
    private final OffFlowCountStore offFlowCountStore;
    private final StepEqpTypeGuard stepEqpTypeGuard;
    private final QueueTimeSupport queueTimeSupport;
    private final ProcessTimeSupport processTimeSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackReleaseResultVO release(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_CREATED.equals(lot.getStatus()), "仅未放行批次可放行");
        AssertUtil.notNull(lot.getRouteId(), "请先指定工艺路线");

        MesRoute route = mesRouteMapper.selectById(lot.getRouteId());
        AssertUtil.notNull(route, "路线不存在");
        AssertUtil.isTrue(route.getStatus() != null && route.getStatus() == 1, "路线已停用");

        MesRouteVersion active = mesRouteVersionMapper.selectOne(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, route.getId())
                .eq(MesRouteVersion::getStatus, ROUTE_ACTIVE)
                .last("LIMIT 1"));
        AssertUtil.notNull(active, "路线无生效版本，无法放行");

        MesRouteStep firstStep = mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, active.getId())
                .orderByAsc(MesRouteStep::getSortNo)
                .last("LIMIT 1"));
        AssertUtil.notNull(firstStep, "生效版本无步骤，无法放行");

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();

        lot.setRouteVersionId(active.getId());
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(firstStep.getSortNo());
        lot.setCurrentStepId(firstStep.getStepId());
        lot.setCurrentEqpId(null);
        lot.setReworkCounts(null);
        lot.setOffFlowCounts(null);
        clearOffFlow(lot);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        queueTimeSupport.clearPersisted(lot);
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_RELEASE, fromStatus, STATUS_WAIT, fromSortNo, firstStep.getSortNo(),
                firstStep.getStepId(), null, active.getId(), "放行进首站", null, null, null);

        TrackReleaseResultVO vo = new TrackReleaseResultVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setRouteVersionId(active.getId());
        vo.setRouteVersionNo(active.getVersionNo());
        vo.setStatus(STATUS_WAIT);
        vo.setCurrentSortNo(firstStep.getSortNo());
        vo.setCurrentStepId(firstStep.getStepId());
        return vo;
    }

    /**
     * 修改批次，扭转批次状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO trackIn(Long lotId, Long eqpId) {
        MesLot lot = requireExecutableLot(lotId);
        // Future Hold PRE：独立事务激活后，本事务 assert 拦截开工（激活不回滚）
        futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);
        holdService.assertNoActive(lotId);
        queueTimeSupport.assertAndSettleOnTrackIn(lot);
        holdService.assertNoActive(lotId);
        mesEqpService.assertUsable(eqpId);
        dispatchService.assertReserveMatch(lotId, eqpId);// 预约匹配
        dispatchService.assertNotOffFlowAnchored(eqpId, lotId);// Off-Flow 锚点占台
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可开工");
        AssertUtil.isTrue(lot.getQty() != null && lot.getQty() >= 1, "数量为0不可开工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法开工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        stepEqpTypeGuard.requireMatch(lot, eqpId);// 设备类型必须匹配
        recipeFacade.assertQualified(current.getStepId(), eqpId);
        RecipeResolveVO recipe = recipeFacade.resolve(current.getStepId(), eqpId);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();

        lot.setStatus(STATUS_PROCESSING);
        lot.setCurrentEqpId(eqpId);
        // 本站配了加工时长就按下秒表；没配则后面清掉库里可能残留的开表时间
        processTimeSupport.startOnTrackIn(lot, current);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        if (!processTimeSupport.isConstrained(current)) {
            processTimeSupport.clearPersisted(lot);
        }
        wipProjectionService.syncFromLot(lot);

        Long txId = writeTxLog(lot, TX_TRACK_IN, fromStatus, STATUS_PROCESSING, fromSortNo, fromSortNo,
                current.getStepId(), eqpId, lot.getRouteVersionId(), "开工",
                recipe != null ? recipe.getRecipeId() : null,
                recipe != null ? recipe.getVersionId() : null, buildTrackInExt(lot, eqpId));
        dispatchService.consumeOnTrackIn(lotId, eqpId, txId);// 消耗预约

        return toTxnVo(lot, TX_TRACK_IN, false);
    }

    /**
     * 分批：把一批拆成多批
     * 父 Lot 保留余量，按 qty 建子 Lot；子继承 Route 快照与当前站，写谱系 + tx_log。
     * 仅 wait、非 Hold、非 Off-Flow；数量守恒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackSplitResultVO split(Long parentLotId, List<TrackSplitChildDTO> children,
                                    String reasonCode, String remark) {
        AssertUtil.notNull(parentLotId, "父批次不能为空");
        AssertUtil.isTrue(children != null && !children.isEmpty(), "子批次列表不能为空");

        MesLot parent = requireExecutableLot(parentLotId);
        holdService.assertNoActive(parentLotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(parent.getStatus()), "仅等待加工状态可分批");
        AssertUtil.isTrue(!isOffFlow(parent), "Off-Flow 中不可分批");
        AssertUtil.notNull(parent.getCurrentSortNo(), "当前站未知，无法分批");
        AssertUtil.isTrue(parent.getQty() != null && parent.getQty() >= 1, "父批数量不足");

        int sumChild = 0;
        for (TrackSplitChildDTO child : children) {
            AssertUtil.notNull(child.getQty(), "子批数量不能为空");
            AssertUtil.isTrue(child.getQty() >= 1, "子批数量至少为1");
            sumChild += child.getQty();
        }
        AssertUtil.isTrue(sumChild <= parent.getQty(), "子批数量之和不能超过父批");

        int qtyBefore = parent.getQty();
        int qtyAfter = qtyBefore - sumChild;
        String fromStatus = parent.getStatus();
        Integer fromSortNo = parent.getCurrentSortNo();
        long userId = StpUtil.getLoginIdAsLong();

        List<TrackSplitResultVO.LotBrief> childBriefs = new ArrayList<>(children.size());
        cn.hutool.json.JSONArray childArr = new cn.hutool.json.JSONArray();
        int seqBase = nextChildSeqBase(parent.getLotNo());

        for (int i = 0; i < children.size(); i++) {
            TrackSplitChildDTO req = children.get(i);
            String childNo = blankToNull(req.getLotNo());
            if (childNo == null) {
                childNo = parent.getLotNo() + "." + String.format("%02d", seqBase + i);
                Long exists = mesLotMapper.selectCount(new LambdaQueryWrapper<MesLot>()
                        .eq(MesLot::getLotNo, childNo));
                AssertUtil.isTrue(exists == 0, "批次号已存在: " + childNo);
            } else {
                Long exists = mesLotMapper.selectCount(new LambdaQueryWrapper<MesLot>()
                        .eq(MesLot::getLotNo, childNo));
                AssertUtil.isTrue(exists == 0, "批次号已存在: " + childNo);
            }

            MesLot child = new MesLot();
            child.setLotNo(childNo);
            child.setProductCode(parent.getProductCode());
            child.setQty(req.getQty());
            child.setScrapQty(0);
            child.setPriority(parent.getPriority());
            child.setHotFlag(parent.getHotFlag() != null ? parent.getHotFlag() : 0);
            child.setCustomerLot(parent.getCustomerLot());
            child.setParentLotId(parent.getId());
            child.setRouteId(parent.getRouteId());
            child.setRouteVersionId(parent.getRouteVersionId());
            child.setCurrentSortNo(parent.getCurrentSortNo());
            child.setCurrentStepId(parent.getCurrentStepId());
            child.setCurrentEqpId(null);
            child.setReworkCounts(null);
            child.setOffFlowCounts(null);
            clearOffFlow(child);
            child.setQtimeFromSort(null);
            child.setQtimeToSort(null);
            child.setQtimeStartedAt(null);
            child.setQtimeMaxMin(null);
            child.setQtimeOnViolate(null);
            child.setStatus(STATUS_WAIT);
            child.setRemark(null);
            child.setVersion(0);
            child.setCreateBy(userId);
            child.setUpdateBy(userId);
            mesLotMapper.insert(child);
            wipProjectionService.syncFromLot(child);

            TrackSplitResultVO.LotBrief brief = new TrackSplitResultVO.LotBrief();
            brief.setLotId(child.getId());
            brief.setLotNo(child.getLotNo());
            brief.setQty(child.getQty());
            childBriefs.add(brief);

            JSONObject item = new JSONObject();
            item.set("lotId", child.getId());
            item.set("lotNo", child.getLotNo());
            item.set("qty", child.getQty());
            childArr.add(item);
        }

        parent.setQty(qtyAfter);
        parent.setUpdateBy(userId);
        int rows = mesLotMapper.updateById(parent);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(parent);

        JSONObject ext = new JSONObject();
        ext.set("parentLotId", parent.getId());
        ext.set("parentQtyBefore", qtyBefore);
        ext.set("parentQtyAfter", qtyAfter);
        ext.set("children", childArr);
        if (StringUtils.hasText(reasonCode)) {
            ext.set("reasonCode", reasonCode.trim());
        }

        Long txId = writeTxLog(parent, TX_SPLIT, fromStatus, STATUS_WAIT, fromSortNo, fromSortNo,
                parent.getCurrentStepId(), null, parent.getRouteVersionId(),
                StringUtils.hasText(remark) ? remark.trim() : "分批",
                null, null, ext.toString());

        for (TrackSplitResultVO.LotBrief brief : childBriefs) {
            MesLotGenealogy gene = new MesLotGenealogy();
            gene.setTxnType("split");
            gene.setParentLotId(parent.getId());
            gene.setChildLotId(brief.getLotId());
            gene.setQty(brief.getQty());
            gene.setTxId(txId);
            gene.setReasonCode(StringUtils.hasText(reasonCode) ? reasonCode.trim() : null);
            gene.setCreateBy(userId);
            gene.setCreateTime(LocalDateTime.now());
            mesLotGenealogyMapper.insert(gene);
        }

        TrackSplitResultVO.LotBrief parentBrief = new TrackSplitResultVO.LotBrief();
        parentBrief.setLotId(parent.getId());
        parentBrief.setLotNo(parent.getLotNo());
        parentBrief.setQty(parent.getQty());

        TrackSplitResultVO result = new TrackSplitResultVO();
        result.setParent(parentBrief);
        result.setChildren(childBriefs);
        result.setTxId(txId);
        return result;
    }

    /**
     * 合批：把多个源 Lot 并入主 Lot。
     * 主 Lot 保留号与站位，qty 累加；源 qty=0、status=merged、写 merged_to_lot_id。
     * 须同产品 / route_version / 当前站；仅 wait、非 Hold、非 Off-Flow；数量守恒。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackMergeResultVO merge(Long mainLotId, List<Long> sourceLotIds,
                                    String reasonCode, String remark) {
        AssertUtil.notNull(mainLotId, "主批次不能为空");
        AssertUtil.isTrue(sourceLotIds != null && !sourceLotIds.isEmpty(), "源批次列表不能为空");

        // 去重且禁止主批出现在源列表
        LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
        for (Long id : sourceLotIds) {
            AssertUtil.notNull(id, "源批次不能为空");
            AssertUtil.isTrue(!Objects.equals(id, mainLotId), "源批次不能包含主批次");
            sourceIds.add(id);
        }
        AssertUtil.isTrue(!sourceIds.isEmpty(), "源批次列表不能为空");

        // 按 id 升序加行锁，避免多 Lot 合批死锁
        TreeSet<Long> lockOrder = new TreeSet<>();
        lockOrder.add(mainLotId);
        lockOrder.addAll(sourceIds);
        Map<Long, MesLot> locked = new HashMap<>();
        for (Long id : lockOrder) {
            MesLot row = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                    .eq(MesLot::getId, id)
                    .last("FOR UPDATE"));
            AssertUtil.notNull(row, "批次不存在: " + id);
            AssertUtil.notNull(row.getRouteVersionId(), "批次未放行: " + row.getLotNo());
            locked.put(id, row);
        }

        MesLot main = locked.get(mainLotId);
        holdService.assertNoActive(mainLotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(main.getStatus()), "仅等待加工状态可合批");
        AssertUtil.isTrue(!isOffFlow(main), "Off-Flow 中不可合批");
        AssertUtil.notNull(main.getCurrentSortNo(), "主批当前站未知，无法合批");
        AssertUtil.isTrue(main.getQty() != null && main.getQty() >= 0, "主批数量非法");

        int qtyBefore = main.getQty();
        int sumSource = 0;
        String fromStatus = main.getStatus();
        Integer fromSortNo = main.getCurrentSortNo();
        long userId = StpUtil.getLoginIdAsLong();

        List<TrackMergeResultVO.MergedBrief> mergedBriefs = new ArrayList<>(sourceIds.size());
        JSONArray sourceArr = new JSONArray();
        List<MesLot> sources = new ArrayList<>(sourceIds.size());

        for (Long sourceId : sourceIds) {
            MesLot source = locked.get(sourceId);
            holdService.assertNoActive(sourceId);
            AssertUtil.isTrue(STATUS_WAIT.equals(source.getStatus()),
                    "源批仅等待加工可合批: " + source.getLotNo());
            AssertUtil.isTrue(!isOffFlow(source), "源批 Off-Flow 中不可合批: " + source.getLotNo());
            AssertUtil.isTrue(source.getQty() != null && source.getQty() >= 1,
                    "源批数量不足: " + source.getLotNo());
            assertMergeCompatible(main, source);

            sumSource += source.getQty();
            sources.add(source);
        }

        // 主批累加数量
        int qtyAfter = qtyBefore + sumSource;
        main.setQty(qtyAfter);
        main.setUpdateBy(userId);
        int mainRows = mesLotMapper.updateById(main);
        AssertUtil.isTrue(mainRows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(main);

        // 源批闭合成 merged，并从 WIP 投影移除
        for (MesLot source : sources) {
            int mergedQty = source.getQty();
            source.setQty(0);
            source.setStatus(STATUS_MERGED);
            source.setMergedToLotId(main.getId());
            source.setUpdateBy(userId);
            int rows = mesLotMapper.updateById(source);
            AssertUtil.isTrue(rows > 0, "源批已被他人修改，请刷新后重试: " + source.getLotNo());
            wipProjectionService.syncFromLot(source);

            TrackMergeResultVO.MergedBrief brief = new TrackMergeResultVO.MergedBrief();
            brief.setLotId(source.getId());
            brief.setLotNo(source.getLotNo());
            brief.setQtyMerged(mergedQty);
            mergedBriefs.add(brief);

            JSONObject item = new JSONObject();
            item.set("lotId", source.getId());
            item.set("lotNo", source.getLotNo());
            item.set("qty", mergedQty);
            sourceArr.add(item);
        }

        JSONObject ext = new JSONObject();
        ext.set("mainLotId", main.getId());
        ext.set("mainQtyBefore", qtyBefore);
        ext.set("mainQtyAfter", qtyAfter);
        ext.set("sources", sourceArr);
        if (StringUtils.hasText(reasonCode)) {
            ext.set("reasonCode", reasonCode.trim());
        }

        Long txId = writeTxLog(main, TX_MERGE, fromStatus, STATUS_WAIT, fromSortNo, fromSortNo,
                main.getCurrentStepId(), null, main.getRouteVersionId(),
                StringUtils.hasText(remark) ? remark.trim() : "合批",
                null, null, ext.toString());

        // 谱系：parent=主，child=源，每源一行
        for (TrackMergeResultVO.MergedBrief brief : mergedBriefs) {
            MesLotGenealogy gene = new MesLotGenealogy();
            gene.setTxnType("merge");
            gene.setParentLotId(main.getId());
            gene.setChildLotId(brief.getLotId());
            gene.setQty(brief.getQtyMerged());
            gene.setTxId(txId);
            gene.setReasonCode(StringUtils.hasText(reasonCode) ? reasonCode.trim() : null);
            gene.setCreateBy(userId);
            gene.setCreateTime(LocalDateTime.now());
            mesLotGenealogyMapper.insert(gene);
        }

        TrackMergeResultVO.LotBrief mainBrief = new TrackMergeResultVO.LotBrief();
        mainBrief.setLotId(main.getId());
        mainBrief.setLotNo(main.getLotNo());
        mainBrief.setQty(main.getQty());

        TrackMergeResultVO result = new TrackMergeResultVO();
        result.setMain(mainBrief);
        result.setMerged(mergedBriefs);
        result.setTxId(txId);
        return result;
    }

    /**
     * 可合入主批的候选：同产品/快照/站、wait、qty≥1，排除 Hold 与 Off-Flow。
     */
    @Override
    public List<TrackMergeCandidateVO> mergeCandidates(Long mainLotId) {
        AssertUtil.notNull(mainLotId, "主批次不能为空");
        MesLot main = requireExecutableLot(mainLotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(main.getStatus()), "仅等待加工主批可查候选");
        AssertUtil.notNull(main.getCurrentSortNo(), "主批当前站未知");

        List<MesLot> rows = mesLotMapper.selectList(new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getStatus, STATUS_WAIT)
                .eq(MesLot::getRouteVersionId, main.getRouteVersionId())
                .eq(MesLot::getCurrentSortNo, main.getCurrentSortNo())
                .ne(MesLot::getId, mainLotId)
                .ge(MesLot::getQty, 1)
                .orderByAsc(MesLot::getLotNo));

        List<TrackMergeCandidateVO> list = new ArrayList<>();
        for (MesLot row : rows) {
            if (isOffFlow(row)) {
                continue;
            }
            if (!Objects.equals(row.getProductCode(), main.getProductCode())) {
                continue;
            }
            if (!Objects.equals(row.getCurrentStepId(), main.getCurrentStepId())) {
                continue;
            }
            if (holdService.hasActive(row.getId())) {
                continue;
            }
            TrackMergeCandidateVO vo = new TrackMergeCandidateVO();
            vo.setLotId(row.getId());
            vo.setLotNo(row.getLotNo());
            vo.setQty(row.getQty());
            vo.setProductCode(row.getProductCode());
            vo.setRouteVersionId(row.getRouteVersionId());
            vo.setCurrentSortNo(row.getCurrentSortNo());
            vo.setCurrentStepId(row.getCurrentStepId());
            vo.setStatus(row.getStatus());
            list.add(vo);
        }
        return list;
    }

    /**
     * 报废：qty↓ scrap_qty↑；scrapQty==原qty 时升格全批 scrapped。
     * 仅 wait、非 Hold、非 Off-Flow；原因码白名单必填。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackScrapResultVO scrap(Long lotId, Integer scrapQty, String reasonCode, String remark) {
        AssertUtil.notNull(lotId, "批次不能为空");
        AssertUtil.notNull(scrapQty, "报废数量不能为空");
        AssertUtil.isTrue(scrapQty >= 1, "报废数量至少为1");
        AssertUtil.isTrue(StringUtils.hasText(reasonCode), "原因码不能为空");

        String code = reasonCode.trim();
        AssertUtil.isTrue(isScrapReasonAllowed(code), "原因码非法: " + code);
        String remarkTrim = StringUtils.hasText(remark) ? remark.trim() : null;
        if ("OTHER".equals(code)) {
            AssertUtil.isTrue(StringUtils.hasText(remarkTrim), "原因码为其他时备注必填");
        }

        MesLot lot = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getId, lotId)
                .last("FOR UPDATE"));
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.notNull(lot.getRouteVersionId(), "批次未放行");

        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可报废");
        AssertUtil.isTrue(!isOffFlow(lot), "Off-Flow 中不可报废");
        AssertUtil.isTrue(lot.getQty() != null && lot.getQty() >= 1, "数量不足，无法报废");
        AssertUtil.isTrue(scrapQty <= lot.getQty(), "报废数量不能超过当前数量");

        int qtyBefore = lot.getQty();
        int scrapBefore = lot.getScrapQty() != null ? lot.getScrapQty() : 0;
        long scrapAfterLong = (long) scrapBefore + scrapQty;
        AssertUtil.isTrue(scrapAfterLong <= Integer.MAX_VALUE, "累计报废数量溢出");
        int scrapAfter = (int) scrapAfterLong;
        int qtyAfter = qtyBefore - scrapQty;
        boolean full = qtyAfter == 0;
        String mode = full ? "full" : "partial";
        String fromStatus = lot.getStatus();
        String toStatus = full ? STATUS_SCRAPPED : STATUS_WAIT;
        Integer fromSortNo = lot.getCurrentSortNo();
        long userId = StpUtil.getLoginIdAsLong();

        lot.setQty(qtyAfter);
        lot.setScrapQty(scrapAfter);
        lot.setStatus(toStatus);
        lot.setUpdateBy(userId);
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        JSONObject ext = new JSONObject();
        ext.set("txn", "scrap");
        ext.set("mode", mode);
        ext.set("scrapQty", scrapQty);
        ext.set("qtyBefore", qtyBefore);
        ext.set("qtyAfter", qtyAfter);
        ext.set("scrapQtyBefore", scrapBefore);
        ext.set("scrapQtyAfter", scrapAfter);
        ext.set("reasonCode", code);
        String extJson = ext.toString();
        AssertUtil.isTrue(extJson.length() <= 512, "报废扩展信息过长，请缩短备注");

        Long txId = writeTxLog(lot, TX_SCRAP, fromStatus, toStatus, fromSortNo, fromSortNo,
                lot.getCurrentStepId(), lot.getCurrentEqpId(), lot.getRouteVersionId(),
                remarkTrim != null ? remarkTrim : "报废",
                null, null, extJson);

        TrackScrapResultVO result = new TrackScrapResultVO();
        result.setLotId(lot.getId());
        result.setLotNo(lot.getLotNo());
        result.setMode(mode);
        result.setQty(lot.getQty());
        result.setScrapQty(lot.getScrapQty());
        result.setStatus(lot.getStatus());
        result.setTxId(txId);
        return result;
    }

    @Override
    public List<TrackScrapReasonVO> scrapReasonCodes() {
        return SCRAP_REASON_CODES;
    }

    private static boolean isScrapReasonAllowed(String code) {
        for (TrackScrapReasonVO item : SCRAP_REASON_CODES) {
            if (item.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 数量调整：只改 qty；不改 scrap_qty/status；原因码独立白名单。
     * 仅 wait、非 Hold、非 Off-Flow。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackBonusResultVO bonus(Long lotId, Integer delta, String reasonCode, String remark) {
        AssertUtil.notNull(lotId, "批次不能为空");
        AssertUtil.notNull(delta, "调整数量不能为空");
        AssertUtil.isTrue(delta != 0, "调整数量不能为0");
        AssertUtil.isTrue(StringUtils.hasText(reasonCode), "原因码不能为空");

        String code = reasonCode.trim();
        AssertUtil.isTrue(isBonusReasonAllowed(code), "原因码非法: " + code);
        String remarkTrim = StringUtils.hasText(remark) ? remark.trim() : null;
        if ("OTHER".equals(code)) {
            AssertUtil.isTrue(StringUtils.hasText(remarkTrim), "原因码为其他时备注必填");
        }

        MesLot lot = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getId, lotId)
                .last("FOR UPDATE"));
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.notNull(lot.getRouteVersionId(), "批次未放行");

        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅等待加工状态可数量调整");
        AssertUtil.isTrue(!isOffFlow(lot), "Off-Flow 中不可数量调整");

        int qtyBefore = lot.getQty() != null ? lot.getQty() : 0;
        long qtyAfterLong = (long) qtyBefore + delta;
        AssertUtil.isTrue(qtyAfterLong >= 0, "调整后数量不能为负");
        AssertUtil.isTrue(qtyAfterLong <= Integer.MAX_VALUE, "调整后数量溢出");
        int qtyAfter = (int) qtyAfterLong;
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        long userId = StpUtil.getLoginIdAsLong();

        lot.setQty(qtyAfter);
        lot.setUpdateBy(userId);
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        JSONObject ext = new JSONObject();
        ext.set("txn", "bonus");
        ext.set("delta", delta);
        ext.set("qtyBefore", qtyBefore);
        ext.set("qtyAfter", qtyAfter);
        ext.set("reasonCode", code);
        String extJson = ext.toString();
        AssertUtil.isTrue(extJson.length() <= 512, "调整扩展信息过长，请缩短备注");

        Long txId = writeTxLog(lot, TX_BONUS, fromStatus, fromStatus, fromSortNo, fromSortNo,
                lot.getCurrentStepId(), lot.getCurrentEqpId(), lot.getRouteVersionId(),
                remarkTrim != null ? remarkTrim : "数量调整",
                null, null, extJson);

        TrackBonusResultVO result = new TrackBonusResultVO();
        result.setLotId(lot.getId());
        result.setLotNo(lot.getLotNo());
        result.setDelta(delta);
        result.setQty(lot.getQty());
        result.setScrapQty(lot.getScrapQty() != null ? lot.getScrapQty() : 0);
        result.setStatus(lot.getStatus());
        result.setTxId(txId);
        return result;
    }

    @Override
    public List<TrackBonusReasonVO> bonusReasonCodes() {
        return BONUS_REASON_CODES;
    }

    private static boolean isBonusReasonAllowed(String code) {
        for (TrackBonusReasonVO item : BONUS_REASON_CODES) {
            if (item.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 合批同质校验：产品、工艺快照、当前站序与工序须一致 */
    private void assertMergeCompatible(MesLot main, MesLot source) {
        AssertUtil.isTrue(Objects.equals(main.getProductCode(), source.getProductCode()),
                "产品不一致，不可合批: " + source.getLotNo());
        AssertUtil.isTrue(Objects.equals(main.getRouteVersionId(), source.getRouteVersionId()),
                "工艺快照不一致，不可合批: " + source.getLotNo());
        AssertUtil.isTrue(Objects.equals(main.getCurrentSortNo(), source.getCurrentSortNo()),
                "当前站不一致，不可合批: " + source.getLotNo());
        AssertUtil.isTrue(Objects.equals(main.getCurrentStepId(), source.getCurrentStepId()),
                "当前工序不一致，不可合批: " + source.getLotNo());
    }

    /** 计算子批号后缀起点：扫描已有 {parentLotNo}.NN，返回 max+1 */
    private int nextChildSeqBase(String parentLotNo) {
        String prefix = parentLotNo + ".";
        List<MesLot> existing = mesLotMapper.selectList(new LambdaQueryWrapper<MesLot>()
                .likeRight(MesLot::getLotNo, prefix));
        int max = 0;
        for (MesLot row : existing) {
            String suffix = row.getLotNo().substring(prefix.length());
            if (!StringUtils.hasText(suffix) || suffix.contains(".")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return max + 1;
    }

    /** 空白串转 null */
    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 构建 TrackIn 扩展信息
     */
    private String buildTrackInExt(MesLot lot, Long eqpId) {
        JSONObject ext = new JSONObject();
        String required = stepEqpTypeGuard.resolveRequired(lot);
        if (StringUtils.hasText(required)) {
            ext.set("eqpTypeRequired", required);
        }
        MesEqp eqp = mesEqpMapper.selectById(eqpId);
        if (eqp != null && StringUtils.hasText(eqp.getEqpType())) {
            ext.set("eqpTypeActual", eqp.getEqpType().trim());
        }
        if (eqpId != null) {
            ext.set("eqpId", String.valueOf(eqpId));
        }
        processTimeSupport.putTrackInExt(ext, lot);
        return ext.isEmpty() ? null : ext.toString();
    }

    /**
     * 完工选边：
     * - 有 resultCode → 只匹配当前站 branch，未命中直接失败（不降级走默认）
     * - 无 resultCode → 走 normal；旧快照无边表则回退步骤 next_sort_no
     * - 永不走 rework（返工必须调 /track/rework）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO trackOut(Long lotId, String resultCode) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_PROCESSING.equals(lot.getStatus()), "仅加工中状态可完工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法完工");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        MesRouteStep current = requireCurrentStep(lot);
        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();

        RouteTrackOutDecision decision = routeEdgeResolver.resolveTrackOut(
                lot.getRouteVersionId(), current, resultCode);

        // 太短直接拒；太长只打标记，出站成功后再锁批
        ProcessTimeSupport.SettleResult ptSettle = processTimeSupport.assertOnTrackOut(lot, current);
        // 这站要采但没合格，拦住，不写出站履历
        edcFacade.assertClearToTrackOut(
                lot.getId(), lot.getRouteVersionId(), lot.getCurrentSortNo(), current.getStepId());

        // 旁路末站无下一站：不完工，走 Resume 回锚点
        if (decision.isCompleted() && isOffFlow(lot)) {
            processTimeSupport.clearPersisted(lot);
            TrackTxnResultVO resumed = doResumeOffFlow(lot, fromStatus, fromSortNo, fromEqpId, true, null);
            processTimeSupport.disposeAfterTrackOut(lot, ptSettle);
            return resumed;
        }

        String toStatus;
        if (decision.isCompleted()) {
            toStatus = STATUS_COMPLETED;
            lot.setStatus(STATUS_COMPLETED);
            lot.setCurrentEqpId(null);
        } else {
            toStatus = STATUS_WAIT;
            lot.setStatus(STATUS_WAIT);
            lot.setCurrentSortNo(decision.getToSortNo());
            lot.setCurrentStepId(decision.getToStepId());
            lot.setCurrentEqpId(null);
        }

        // 出站后秒表作废（先内存再落库）
        processTimeSupport.clear(lot);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        processTimeSupport.clearPersisted(lot);
        wipProjectionService.syncFromLot(lot);

        JSONObject outExt = decision.toExtJson() == null ? new JSONObject()
                : new JSONObject(decision.toExtJson());
        processTimeSupport.putExt(outExt, ptSettle);
        String outExtStr = outExt.isEmpty() ? null : outExt.toString();

        writeTxLog(lot, TX_TRACK_OUT, fromStatus, toStatus, fromSortNo, decision.getToSortNo(),
                decision.getToStepId(), fromEqpId, lot.getRouteVersionId(), decision.getRemark(),
                null, null, outExtStr);

        queueTimeSupport.openAfterTrackOut(lot, fromSortNo, decision);

        // 加工超时：出站已成功，这里再 Hold/告警（末站 completed 锁不了就只告警）
        processTimeSupport.disposeAfterTrackOut(lot, ptSettle);

        // Future Hold POST：落新站后再激活（本次 Out 已成功，下次推进被拦）
        if (!decision.isCompleted() && lot.getCurrentSortNo() != null
                && !"held".equals(lot.getStatus())) {
            futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_POST);
        }

        return toTxnVo(lot, TX_TRACK_OUT, decision.isCompleted());
    }

    /**
     * 加工中止：机台上干到一半出状况，合法退回本站 wait。
     * <p>
     * 人话：站别不动、数量不动、不是报废；把机台让出来，秒表清掉，记下为啥中止，以后还能再开工。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO abort(Long lotId, String reasonCode, String remark) {
        AssertUtil.notNull(lotId, "批次不能为空");
        if (!StringUtils.hasText(reasonCode)) {
            throw new BusinessException(ERR_ABORT_REASON_REQUIRED + ": 中止原因不能为空");
        }
        String code = reasonCode.trim();
        if (!isAbortReasonAllowed(code)) {
            throw new BusinessException(ERR_ABORT_REASON_INVALID + ": 原因码非法: " + code);
        }
        String remarkTrim = StringUtils.hasText(remark) ? remark.trim() : null;
        if ("OTHER".equals(code) && !StringUtils.hasText(remarkTrim)) {
            throw new BusinessException(ERR_ABORT_REMARK_REQUIRED + ": 原因码为其他时备注必填");
        }

        MesLot lot = requireExecutableLot(lotId);
        // 锁批中先放行再说，别绕过 Hold 退站
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_PROCESSING.equals(lot.getStatus()), "仅加工中状态可中止");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法中止");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();
        LocalDateTime processStartedAt = lot.getProcessStartedAt();
        Long elapsedMin = null;
        if (processStartedAt != null) {
            elapsedMin = Math.max(0, Duration.between(processStartedAt, LocalDateTime.now()).toMinutes());
        }

        // updateById 默认不写 null，腾机/清秒表必须用 Wrapper 显式 SET
        long userId = StpUtil.getLoginIdAsLong();
        int ver = lot.getVersion() == null ? 0 : lot.getVersion();
        int rows = mesLotMapper.update(null, new LambdaUpdateWrapper<MesLot>()
                .eq(MesLot::getId, lot.getId())
                .eq(MesLot::getVersion, ver)
                .set(MesLot::getStatus, STATUS_WAIT)
                .set(MesLot::getCurrentEqpId, null)
                .set(MesLot::getProcessStartedAt, null)
                .set(MesLot::getUpdateBy, userId)
                .set(MesLot::getVersion, ver + 1));
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");

        MesLot persisted = mesLotMapper.selectById(lot.getId());
        AssertUtil.notNull(persisted, "批次不存在");
        AssertUtil.isTrue(STATUS_WAIT.equals(persisted.getStatus()), "中止未落库，请重试");
        lot = persisted;

        wipProjectionService.syncFromLot(lot);

        // 正常开工会把预约吃掉；这里只防残留 active 还占着坑
        Long releasedReserveId = dispatchService.releaseActiveOnAbort(lotId, null);

        JSONObject ext = new JSONObject();
        ext.set("reasonCode", code);
        if (remarkTrim != null) {
            ext.set("remark", remarkTrim);
        }
        if (processStartedAt != null) {
            ext.set("processStartedAt", processStartedAt.toString());
            ext.set("processElapsedMin", elapsedMin);
        }
        ext.set("clearedProcessTime", true);
        if (releasedReserveId != null) {
            ext.set("releasedReserveId", releasedReserveId);
        }

        String logRemark = remarkTrim != null ? remarkTrim : "加工中止";
        String extJson = ext.toString();
        if (extJson.length() > 512) {
            ext.remove("remark");
            extJson = ext.toString();
            if (extJson.length() > 512) {
                extJson = "{\"reasonCode\":\"" + code + "\",\"clearedProcessTime\":true}";
            }
        }
        writeTxLog(lot, TX_ABORT, fromStatus, STATUS_WAIT, fromSortNo, fromSortNo,
                lot.getCurrentStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, extJson);

        return toTxnVo(lot, TX_ABORT, false);
    }

    @Override
    public List<TrackAbortReasonVO> abortReasonCodes() {
        return ABORT_REASON_CODES;
    }

    private static boolean isAbortReasonAllowed(String code) {
        for (TrackAbortReasonVO item : ABORT_REASON_CODES) {
            if (item.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 独立移站：人/物流把批挪到下一站，但本站没干活。
     * <p>
     * 人话：状态还是 wait，只换站号；不能乱跳，只能去工艺规定的下一站；末站想结批请走完工，别用搬家冒充。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO move(Long lotId, Integer toSortNo, String remark) {
        AssertUtil.notNull(lotId, "批次不能为空");

        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);

        if (!STATUS_WAIT.equals(lot.getStatus())) {
            throw new BusinessException(ERR_MOVE_NOT_WAIT + ": 仅等待状态可移站，加工中请先完工或中止");
        }
        if (isOffFlow(lot)) {
            throw new BusinessException(ERR_MOVE_OFF_FLOW + ": Off-Flow 中不可移站");
        }
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法移站");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本");

        if (lot.getCurrentEqpId() != null) {
            throw new BusinessException(ERR_MOVE_DIRTY_EQP + ": 批次仍占着机台，请先中止或完工");
        }
        if (lot.getProcessStartedAt() != null) {
            throw new BusinessException(ERR_MOVE_DIRTY_PROCESS_TIME + ": 批次仍有加工计时，请先中止或完工");
        }

        MesRouteStep current = requireCurrentStep(lot);
        // 跟 TrackOut 默认推进同一套 next，避免两套算法对不齐
        RouteTrackOutDecision decision = routeEdgeResolver.resolveTrackOut(
                lot.getRouteVersionId(), current, null);
        if (decision.isCompleted() || decision.getToSortNo() == null || decision.getToStepId() == null) {
            throw new BusinessException(ERR_MOVE_NO_NEXT + ": 已无下一站，末站请走完工，不可用移站结批");
        }
        if (toSortNo != null && !Objects.equals(toSortNo, decision.getToSortNo())) {
            throw new BusinessException(ERR_MOVE_TARGET_NOT_NEXT
                    + ": 目标站必须是下一站 sortNo=" + decision.getToSortNo());
        }

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromStepId = lot.getCurrentStepId();
        Integer targetSortNo = decision.getToSortNo();
        Long targetStepId = decision.getToStepId();

        long userId = StpUtil.getLoginIdAsLong();
        int ver = lot.getVersion() == null ? 0 : lot.getVersion();
        int rows = mesLotMapper.update(null, new LambdaUpdateWrapper<MesLot>()
                .eq(MesLot::getId, lot.getId())
                .eq(MesLot::getVersion, ver)
                .eq(MesLot::getStatus, STATUS_WAIT)
                .set(MesLot::getCurrentSortNo, targetSortNo)
                .set(MesLot::getCurrentStepId, targetStepId)
                .set(MesLot::getUpdateBy, userId)
                .set(MesLot::getVersion, ver + 1));
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");

        MesLot persisted = mesLotMapper.selectById(lot.getId());
        AssertUtil.notNull(persisted, "批次不存在");
        AssertUtil.isTrue(Objects.equals(persisted.getCurrentSortNo(), targetSortNo), "移站未落库，请重试");
        lot = persisted;

        wipProjectionService.syncFromLot(lot);

        // 站已经换了，站间等待窗跟 TrackOut 推进共用同一套开窗逻辑
        queueTimeSupport.openAfterTrackOut(lot, fromSortNo, decision);

        String remarkTrim = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("fromSortNo", fromSortNo);
        ext.set("toSortNo", targetSortNo);
        ext.set("fromStepId", fromStepId);
        ext.set("toStepId", targetStepId);
        ext.set("moveKind", "NEXT");
        if (decision.getEdge() != null) {
            ext.set("edgeId", String.valueOf(decision.getEdge().getId()));
            ext.set("edgeType", decision.getEdge().getEdgeType());
        }
        if (remarkTrim != null) {
            ext.set("remark", remarkTrim);
        }
        String extJson = ext.toString();
        if (extJson.length() > 512) {
            ext.remove("remark");
            extJson = ext.toString();
        }

        String logRemark = remarkTrim != null ? remarkTrim : "独立移站";
        writeTxLog(lot, TX_MOVE, fromStatus, STATUS_WAIT, fromSortNo, targetSortNo,
                fromStepId, null, lot.getRouteVersionId(), logRemark, null, null, extJson);

        // 到了新站等待，按预约锁批 PRE 时机检查（跟 Skip 一样）
        if (lot.getCurrentSortNo() != null && !"held".equals(lot.getStatus())) {
            futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);
        }

        return toTxnVo(lot, TX_MOVE, false);
    }

    /**
     * 回流：按快照 rework 边跳回前序站，按触发站累计次数，超限拒绝。
     * 与 TrackOut 分支不同：独立事务 + track:rework 权限。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO rework(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "仅等待或加工中状态可返工");
        AssertUtil.isFalse(isOffFlow(lot), "Off-Flow 中不可返工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法返工");
        AssertUtil.notNull(toSortNo, "回流目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findRework(lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(edge, "当前站未配置回流至目标站");
        AssertUtil.notNull(edge.getMaxReworkCount(), "回流次数上限未配置");
        AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限异常");

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "返工原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "返工原因不匹配");
        }

        int used = reworkCountStore.get(lot, fromSortNo);
        AssertUtil.isTrue(used + 1 <= edge.getMaxReworkCount(), "返工次数已达上限");

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "回流目标站不存在");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        int newCount = used + 1;
        reworkCountStore.put(lot, fromSortNo, newCount);

        queueTimeSupport.clearWithLog(lot, "Rework 清除 QueueTime");
        // 返工离开本站，加工秒表作废
        processTimeSupport.clearPersisted(lot);

        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }
        ext.set("reworkCount", newCount);
        ext.set("maxReworkCount", edge.getMaxReworkCount());
        ext.set("edgeId", String.valueOf(edge.getId()));

        writeTxLog(lot, TX_REWORK, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        // 改站后检查目标站 PRE 预约
        futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);

        TrackTxnResultVO vo = toTxnVo(lot, TX_REWORK, false);
        vo.setReworkCount(newCount);
        vo.setMaxReworkCount(edge.getMaxReworkCount());
        return vo;
    }

    /**
     * 前向跳站：仅 wait（未开工）可跳；命中 skip_allow，中间站记履历，落到目标 wait。
     * processing 须先 TrackOut（后置可加 Abort）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO skip(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()),
                "仅等待状态可跳站，加工中请先完工");
        AssertUtil.isFalse(isOffFlow(lot), "Off-Flow 中不可跳站");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法跳站");
        AssertUtil.notNull(toSortNo, "跳站目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findSkip(lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(edge, "当前站未配置跳至目标站");
        // 获取跳站目标站可跳过的中间站
        List<Integer> skipped = routeEdgeResolver.computeSkippedSortNos(
                lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(skipped, "跳站目标不可沿主路径前向到达");
        // 获取跳站目标站不可跳过的中间站
        Integer bad = routeEdgeResolver.findDisallowedSkipSort(
                lot.getRouteVersionId(), fromSortNo, toSortNo, skipped);
        AssertUtil.isTrue(bad == null, "跳站路径含不可跳站: sortNo=" + bad);

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "跳站原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "跳站原因不匹配");
        }

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "跳站目标站不存在");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("edgeType", RouteEdgeTypes.SKIP_ALLOW);
        ext.set("edgeId", String.valueOf(edge.getId()));
        ext.set("fromSortNo", fromSortNo);
        ext.set("toSortNo", toSortNo);
        ext.set("skippedSortNos", skipped);
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }

        writeTxLog(lot, TX_SKIP, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        queueTimeSupport.onSkip(lot, fromSortNo, toSortNo, skipped);

        // 改站后检查目标站 PRE 预约
        futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);
        return toTxnVo(lot, TX_SKIP, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO enterOffFlow(Long lotId, Integer toSortNo, String reasonCode, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "当前状态不可进入 Off-Flow");
        AssertUtil.isFalse(isOffFlow(lot), "已在 Off-Flow 中");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知，无法进入 Off-Flow");
        AssertUtil.notNull(toSortNo, "Off-Flow 目标站不能为空");

        Integer fromSortNo = lot.getCurrentSortNo();
        MesRouteEdge edge = routeEdgeResolver.findOffFlow(lot.getRouteVersionId(), fromSortNo, toSortNo);
        AssertUtil.notNull(edge, "当前站未配置 Off-Flow");

        String reason = reasonCode == null ? null : reasonCode.trim();
        if (StringUtils.hasText(edge.getReasonCodes())) {
            AssertUtil.notBlank(reason, "Off-Flow 原因不能为空");
            Set<String> allowed = Arrays.stream(edge.getReasonCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            AssertUtil.isTrue(allowed.contains(reason), "Off-Flow 原因不匹配");
        }

        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), toSortNo);
        AssertUtil.notNull(target, "Off-Flow 入口站不存在");
        int max = edge.getMaxReworkCount() != null && edge.getMaxReworkCount() >= 1
                ? edge.getMaxReworkCount() : 1;

        int used = offFlowCountStore.get(lot, fromSortNo);
        AssertUtil.isTrue(used + 1 <= max, "Off-Flow 次数已达上限");

        String fromStatus = lot.getStatus();
        Long fromEqpId = lot.getCurrentEqpId();
        int newCount = used + 1;
        offFlowCountStore.put(lot, fromSortNo, newCount);

        lot.setOffFlow(1);
        lot.setOffFlowAnchorSort(fromSortNo);
        lot.setOffFlowAnchorStepId(lot.getCurrentStepId());
        lot.setOffFlowAnchorEqpId(fromEqpId);
        lot.setOffFlowAnchorStatus(fromStatus);
        lot.setStatus(STATUS_WAIT);
        lot.setCurrentSortNo(target.getSortNo());
        lot.setCurrentStepId(target.getStepId());
        lot.setCurrentEqpId(null);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("edgeType", RouteEdgeTypes.OFF_FLOW);
        ext.set("edgeId", String.valueOf(edge.getId()));
        ext.set("fromSortNo", fromSortNo);
        ext.set("toSortNo", toSortNo);
        ext.set("anchorSortNo", fromSortNo);
        ext.set("anchorStatus", fromStatus);
        if (fromEqpId != null) {
            ext.set("anchorEqpId", String.valueOf(fromEqpId));
        }
        if (StringUtils.hasText(reason)) {
            ext.set("reasonCode", reason);
        }
        ext.set("offFlowCount", newCount);
        ext.set("maxOffFlowCount", max);

        writeTxLog(lot, TX_OFF_FLOW, fromStatus, STATUS_WAIT, fromSortNo, target.getSortNo(),
                target.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        // 改站后检查目标站 PRE 预约
        futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);

        TrackTxnResultVO vo = toTxnVo(lot, TX_OFF_FLOW, false);
        vo.setOffFlowCount(newCount);
        vo.setMaxOffFlowCount(max);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrackTxnResultVO resumeOffFlow(Long lotId, String remark) {
        MesLot lot = requireExecutableLot(lotId);
        holdService.assertNoActive(lotId);
        AssertUtil.isTrue(isOffFlow(lot), "不在 Off-Flow 中");
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()), "仅旁路等待状态可回主路径，加工中请先完工");
        AssertUtil.notNull(lot.getCurrentSortNo(), "当前站未知");
        AssertUtil.isTrue(routeEdgeResolver.isOffFlowTerminal(lot.getRouteVersionId(), lot.getCurrentSortNo()),
                "仅旁路末站可回主路径");

        String fromStatus = lot.getStatus();
        Integer fromSortNo = lot.getCurrentSortNo();
        Long fromEqpId = lot.getCurrentEqpId();
        return doResumeOffFlow(lot, fromStatus, fromSortNo, fromEqpId, false, remark);
    }

    /**
     * Off-Flow 回主路径：Lot 回到进入时锚点站，尝试恢复锚点状态/机台。
     * 锚点机台不可用或已被别批占用时降级为 wait；viaTrackOut=true 表示旁路末站 TrackOut 自动触发。
     */
    private TrackTxnResultVO doResumeOffFlow(MesLot lot, String fromStatus, Integer fromSortNo,
                                             Long fromEqpId, boolean viaTrackOut, String remark) {
        AssertUtil.isTrue(isOffFlow(lot), "不在 Off-Flow 中");
        AssertUtil.notNull(lot.getOffFlowAnchorSort(), "Off-Flow 锚点缺失");

        MesRouteStep anchor = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getOffFlowAnchorSort());
        AssertUtil.notNull(anchor, "Off-Flow 锚点站不存在");

        String restoreStatus = StringUtils.hasText(lot.getOffFlowAnchorStatus())
                ? lot.getOffFlowAnchorStatus() : STATUS_WAIT;
        AssertUtil.isTrue(STATUS_WAIT.equals(restoreStatus) || STATUS_PROCESSING.equals(restoreStatus),
                "Off-Flow 锚点状态异常");
        Long restoreEqp = STATUS_PROCESSING.equals(restoreStatus) ? lot.getOffFlowAnchorEqpId() : null;
        Integer anchorSort = lot.getOffFlowAnchorSort();
        boolean degraded = false;

        if (STATUS_PROCESSING.equals(restoreStatus) && restoreEqp != null) {
            try {
                mesEqpService.assertUsable(restoreEqp);
            } catch (BusinessException ex) {
                restoreStatus = STATUS_WAIT;
                restoreEqp = null;
                degraded = true;
            }
            if (!degraded) {
                MesLot other = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                        .eq(MesLot::getCurrentEqpId, restoreEqp)
                        .eq(MesLot::getStatus, STATUS_PROCESSING)
                        .ne(MesLot::getId, lot.getId())
                        .last("LIMIT 1"));
                if (other != null) {
                    restoreStatus = STATUS_WAIT;
                    restoreEqp = null;
                    degraded = true;
                }
            }
        }

        clearOffFlow(lot);
        lot.setStatus(restoreStatus);
        lot.setCurrentSortNo(anchor.getSortNo());
        lot.setCurrentStepId(anchor.getStepId());
        lot.setCurrentEqpId(restoreEqp);
        lot.setUpdateBy(StpUtil.getLoginIdAsLong());
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        String logRemark = StringUtils.hasText(remark) ? remark.trim() : null;
        JSONObject ext = new JSONObject();
        ext.set("fromSortNo", fromSortNo);
        ext.set("anchorSortNo", anchorSort);
        ext.set("restoredStatus", restoreStatus);
        if (restoreEqp != null) {
            ext.set("restoredEqpId", String.valueOf(restoreEqp));
        }
        if (degraded) {
            ext.set("degraded", true);
        }
        if (viaTrackOut) {
            ext.set("viaTrackOut", true);
        }

        writeTxLog(lot, TX_OFF_FLOW_RESUME, fromStatus, restoreStatus, fromSortNo, anchor.getSortNo(),
                anchor.getStepId(), fromEqpId, lot.getRouteVersionId(), logRemark, null, null, ext.toString());

        // 回锚点后检查 PRE 预约
        futureHoldService.tryActivate(lot, lot.getCurrentSortNo(), FutureHoldServiceImpl.TIMING_PRE);
        return toTxnVo(lot, TX_OFF_FLOW_RESUME, false);
    }

    /** 现场台上下文：当前站 + 默认下一站 + 可选分支/回流/跳站/Off-Flow 列表 */
    @Override
    public TrackContextVO context(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");

        TrackContextVO vo = new TrackContextVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setStatus(lot.getStatus());
        vo.setRouteId(lot.getRouteId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setCurrentSortNo(lot.getCurrentSortNo());
        vo.setCurrentStepId(lot.getCurrentStepId());
        vo.setCurrentEqpId(lot.getCurrentEqpId());
        vo.setCompleted(STATUS_COMPLETED.equals(lot.getStatus()));
        vo.setCanTrackIn(STATUS_WAIT.equals(lot.getStatus()) && lot.getQty() != null && lot.getQty() >= 1);
        vo.setCanTrackOut(STATUS_PROCESSING.equals(lot.getStatus()));
        vo.setReworkOptions(Collections.emptyList());
        vo.setBranchOptions(Collections.emptyList());
        vo.setSkipOptions(Collections.emptyList());
        vo.setOffFlowOptions(Collections.emptyList());
        vo.setCanRework(false);
        vo.setCanSkip(false);
        vo.setCanEnterOffFlow(false);
        vo.setCanResumeOffFlow(false);
        vo.setCanSplit(STATUS_WAIT.equals(lot.getStatus())
                && !isOffFlow(lot)
                && lot.getQty() != null && lot.getQty() >= 1
                && lot.getRouteVersionId() != null
                && StpUtil.hasPermission("track:split"));
        vo.setCanMerge(STATUS_WAIT.equals(lot.getStatus())
                && !isOffFlow(lot)
                && lot.getRouteVersionId() != null
                && lot.getCurrentSortNo() != null
                && StpUtil.hasPermission("track:merge"));
        vo.setCanScrap(STATUS_WAIT.equals(lot.getStatus())
                && !isOffFlow(lot)
                && lot.getQty() != null && lot.getQty() >= 1
                && lot.getRouteVersionId() != null
                && StpUtil.hasPermission("track:scrap"));
        vo.setCanBonus(STATUS_WAIT.equals(lot.getStatus())
                && !isOffFlow(lot)
                && lot.getRouteVersionId() != null
                && StpUtil.hasPermission("track:bonus"));
        // 加工中 + 有 track:abort 才能中止
        vo.setCanAbort(STATUS_PROCESSING.equals(lot.getStatus())
                && lot.getRouteVersionId() != null
                && lot.getCurrentSortNo() != null
                && !holdService.hasActive(lotId)
                && StpUtil.hasPermission("track:abort"));
        // 独立移站先默认 false，有下一站再打开
        vo.setCanMove(false);
        vo.setNextSortNo(null);
        vo.setNextStepName(null);
        vo.setOffFlow(isOffFlow(lot));
        vo.setOffFlowAnchorSortNo(lot.getOffFlowAnchorSort());
        vo.setReworkCount(lot.getCurrentSortNo() != null ? reworkCountStore.get(lot, lot.getCurrentSortNo()) : 0);
        vo.setPendingFutureHolds(futureHoldService.listPendingByLot(lotId));
        vo.setQueueTime(queueTimeSupport.toContextVo(lot));
        vo.setProcessTime(null); // 加工中才有倒计时，下面按当前站补
        vo.setEdc(null);

        if (lot.getRouteVersionId() != null) {
            MesRouteVersion version = mesRouteVersionMapper.selectById(lot.getRouteVersionId());
            if (version != null) {
                vo.setRouteVersionNo(version.getVersionNo());
            }
        }

        if (lot.getRouteVersionId() == null || lot.getCurrentSortNo() == null) {
            return vo;
        }

        MesRouteStep current = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (current == null) {
            return vo;
        }
        vo.setCurrentStep(toStepVo(current));
        vo.setProcessTime(processTimeSupport.toContextVo(lot, current));
        fillEdcGate(vo, lot, current);

        MesRouteStep next = routeEdgeResolver.resolveDefaultNext(lot.getRouteVersionId(), current);
        if (next != null) {
            MesLotStepVO nextVo = toStepVo(next);
            vo.setNextStep(nextVo);
            vo.setNextSortNo(next.getSortNo());
            vo.setNextStepName(nextVo.getStepName());
            // wait + 有下一站 + 没占机 + 有权限 → 可以只搬家
            vo.setCanMove(STATUS_WAIT.equals(lot.getStatus())
                    && !isOffFlow(lot)
                    && lot.getCurrentEqpId() == null
                    && !holdService.hasActive(lotId)
                    && StpUtil.hasPermission("track:move"));
        }

        List<MesRouteEdge> outEdges = routeEdgeResolver.listNormalAndBranch(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        List<MesRouteEdge> branchEdges = outEdges.stream()
                .filter(e -> RouteEdgeTypes.BRANCH.equals(e.getEdgeType()))
                .toList();
        if (!branchEdges.isEmpty()) {
            Set<Integer> toSorts = branchEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
            List<TrackBranchOptionVO> branchOptions = new ArrayList<>();
            for (MesRouteEdge edge : branchEdges) {
                TrackBranchOptionVO opt = new TrackBranchOptionVO();
                opt.setConditionCode(edge.getConditionCode());
                opt.setToSortNo(edge.getToSortNo());
                fillStepLabels(opt, stepBySort.get(edge.getToSortNo()), stepMap);
                branchOptions.add(opt);
            }
            vo.setBranchOptions(branchOptions);
        }

        List<MesRouteEdge> reworkEdges = routeEdgeResolver.listRework(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (!reworkEdges.isEmpty()) {
            Set<Integer> toSorts = reworkEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));

            List<TrackReworkOptionVO> options = new ArrayList<>();
            int used = reworkCountStore.get(lot, lot.getCurrentSortNo());
            for (MesRouteEdge edge : reworkEdges) {
                int remain = Math.max(0, edge.getMaxReworkCount() - used);
                if (remain <= 0) {
                    continue;
                }
                TrackReworkOptionVO opt = new TrackReworkOptionVO();
                opt.setToSortNo(edge.getToSortNo());
                opt.setMaxReworkCount(edge.getMaxReworkCount());
                opt.setRemainCount(remain);
                if (StringUtils.hasText(edge.getReasonCodes())) {
                    opt.setReasonCodes(Arrays.stream(edge.getReasonCodes().split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList());
                } else {
                    opt.setReasonCodes(Collections.emptyList());
                }
                fillStepLabels(opt, stepBySort.get(edge.getToSortNo()), stepMap);
                options.add(opt);
            }
            vo.setReworkOptions(options);
            boolean statusOk = !isOffFlow(lot)
                    && (STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()));
            vo.setCanRework(statusOk && !options.isEmpty() && StpUtil.hasPermission("track:rework"));
        }

        List<MesRouteEdge> skipEdges = routeEdgeResolver.listSkip(
                lot.getRouteVersionId(), lot.getCurrentSortNo());
        if (!skipEdges.isEmpty() && !isOffFlow(lot)) {
            Set<Integer> toSorts = skipEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
            Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                    lot.getRouteVersionId(), toSorts);
            Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                    stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
            List<TrackSkipOptionVO> skipOptions = new ArrayList<>();
            for (MesRouteEdge edge : skipEdges) {
                List<Integer> skipped = routeEdgeResolver.computeSkippedSortNos(
                        lot.getRouteVersionId(), lot.getCurrentSortNo(), edge.getToSortNo());
                if (skipped == null) {
                    continue;
                }
                Integer bad = routeEdgeResolver.findDisallowedSkipSort(
                        lot.getRouteVersionId(), lot.getCurrentSortNo(), edge.getToSortNo(), skipped);
                if (bad != null) {
                    continue;
                }
                TrackSkipOptionVO opt = new TrackSkipOptionVO();
                opt.setToSortNo(edge.getToSortNo());
                opt.setSkippedSortNos(skipped);
                if (StringUtils.hasText(edge.getReasonCodes())) {
                    opt.setReasonCodes(Arrays.stream(edge.getReasonCodes().split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .toList());
                } else {
                    opt.setReasonCodes(Collections.emptyList());
                }
                MesRouteStep target = stepBySort.get(edge.getToSortNo());
                if (target != null) {
                    MesStep step = stepMap.get(target.getStepId());
                    if (step != null) {
                        opt.setToStepCode(step.getStepCode());
                        opt.setToStepName(step.getStepName());
                    }
                }
                skipOptions.add(opt);
            }
            vo.setSkipOptions(skipOptions);
            vo.setCanSkip(STATUS_WAIT.equals(lot.getStatus())
                    && !skipOptions.isEmpty()
                    && StpUtil.hasPermission("track:skip"));
        }

        // 主路径：组装可进 Off-Flow 的旁路入口；已在旁路：只算能否 Resume
        if (!isOffFlow(lot)) {
            List<MesRouteEdge> offEdges = routeEdgeResolver.listOffFlow(
                    lot.getRouteVersionId(), lot.getCurrentSortNo());
            if (!offEdges.isEmpty()) {
                Set<Integer> toSorts = offEdges.stream().map(MesRouteEdge::getToSortNo).collect(Collectors.toSet());
                Map<Integer, MesRouteStep> stepBySort = routeEdgeResolver.mapRouteStepsBySort(
                        lot.getRouteVersionId(), toSorts);
                Map<Long, MesStep> stepMap = routeEdgeResolver.mapStepsById(
                        stepBySort.values().stream().map(MesRouteStep::getStepId).collect(Collectors.toSet()));
                List<TrackOffFlowOptionVO> offOptions = new ArrayList<>();
                int used = offFlowCountStore.get(lot, lot.getCurrentSortNo());
                for (MesRouteEdge edge : offEdges) {
                    int max = edge.getMaxReworkCount() != null ? edge.getMaxReworkCount() : 1;
                    int remain = Math.max(0, max - used);
                    if (remain <= 0) {
                        continue;
                    }
                    TrackOffFlowOptionVO opt = new TrackOffFlowOptionVO();
                    opt.setToSortNo(edge.getToSortNo());
                    opt.setMaxOffFlowCount(max);
                    opt.setRemainCount(remain);
                    if (StringUtils.hasText(edge.getReasonCodes())) {
                        opt.setReasonCodes(Arrays.stream(edge.getReasonCodes().split(","))
                                .map(String::trim)
                                .filter(StringUtils::hasText)
                                .toList());
                    } else {
                        opt.setReasonCodes(Collections.emptyList());
                    }
                    MesRouteStep target = stepBySort.get(edge.getToSortNo());
                    if (target != null) {
                        MesStep step = stepMap.get(target.getStepId());
                        if (step != null) {
                            opt.setToStepCode(step.getStepCode());
                            opt.setToStepName(step.getStepName());
                        }
                    }
                    offOptions.add(opt);
                }
                vo.setOffFlowOptions(offOptions);
                boolean statusOk = STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus());
                vo.setCanEnterOffFlow(statusOk && !offOptions.isEmpty()
                        && StpUtil.hasPermission("track:off-flow"));
            }
        } else {
            // 旁路末站 + wait + 有权限 → 可回主路径
            boolean terminal = routeEdgeResolver.isOffFlowTerminal(
                    lot.getRouteVersionId(), lot.getCurrentSortNo());
            vo.setCanResumeOffFlow(STATUS_WAIT.equals(lot.getStatus()) && terminal
                    && StpUtil.hasPermission("track:off-flow"));
        }
        return vo;
    }

    /** 加工中才问量测：过不了就把完工按钮灭掉。 */
    private void fillEdcGate(TrackContextVO vo, MesLot lot, MesRouteStep current) {
        if (!STATUS_PROCESSING.equals(lot.getStatus()) || current == null || current.getStepId() == null) {
            return;
        }
        EdcGateResult gate = edcFacade.evaluateGate(
                lot.getId(), lot.getRouteVersionId(), lot.getCurrentSortNo(), current.getStepId());
        TrackEdcVO edc = new TrackEdcVO();
        edc.setRequired(gate.isRequired());
        edc.setClear(gate.isClear());
        edc.setReasonCode(gate.getReasonCode());
        edc.setMessage(gate.getMessage());
        vo.setEdc(edc);
        if (Boolean.TRUE.equals(vo.getCanTrackOut()) && !gate.isClear()) {
            vo.setCanTrackOut(false);
        }
    }

    private static void fillStepLabels(TrackBranchOptionVO opt, MesRouteStep target, Map<Long, MesStep> stepMap) {
        if (target == null) {
            return;
        }
        MesStep step = stepMap.get(target.getStepId());
        if (step != null) {
            opt.setToStepCode(step.getStepCode());
            opt.setToStepName(step.getStepName());
        }
    }

    private static void fillStepLabels(TrackReworkOptionVO opt, MesRouteStep target, Map<Long, MesStep> stepMap) {
        if (target == null) {
            return;
        }
        MesStep step = stepMap.get(target.getStepId());
        if (step != null) {
            opt.setToStepCode(step.getStepCode());
            opt.setToStepName(step.getStepName());
        }
    }

    /** 履历读路径已搬到 History；这里只转发，别再写第二套 SQL。 */
    @Override
    public List<HistoryTxVO> history(Long lotId) {
        return historyFacade.listByLot(lotId);
    }

    private MesLotStepVO toStepVo(MesRouteStep row) {
        MesLotStepVO stepVo = new MesLotStepVO();
        stepVo.setStepId(row.getStepId());
        stepVo.setSortNo(row.getSortNo());
        stepVo.setNextSortNo(row.getNextSortNo());
        stepVo.setEqpType(row.getEqpType());
        stepVo.setStepType(row.getStepType());
        MesStep step = mesStepMapper.selectById(row.getStepId());
        if (step != null) {
            stepVo.setStepCode(step.getStepCode());
            stepVo.setStepName(step.getStepName());
            if (!StringUtils.hasText(stepVo.getEqpType())) {
                stepVo.setEqpType(step.getEqpType());
            }
            if (stepVo.getStepType() == null) {
                stepVo.setStepType(step.getStepType());
            }
        }
        return stepVo;
    }

    /** 获取有效批次 */
    private MesLot requireExecutableLot(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.notNull(lot.getRouteVersionId(), "批次未放行");
        return lot;
    }

    /** 获取当前批次的站点 */
    private MesRouteStep requireCurrentStep(MesLot lot) {
        MesRouteStep current = routeEdgeResolver.findStep(lot.getRouteVersionId(), lot.getCurrentSortNo());
        AssertUtil.notNull(current, "当前站不在路线快照中");
        AssertUtil.isTrue(Objects.equals(current.getStepId(), lot.getCurrentStepId())
                        || lot.getCurrentStepId() == null,
                "当前工序与快照不一致");
        if (lot.getCurrentStepId() == null) {
            lot.setCurrentStepId(current.getStepId());
        }
        return current;
    }

    private TrackTxnResultVO toTxnVo(MesLot lot, String txType, boolean completed) {
        TrackTxnResultVO vo = new TrackTxnResultVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setTxType(txType);
        vo.setStatus(lot.getStatus());
        vo.setCurrentSortNo(lot.getCurrentSortNo());
        vo.setCurrentStepId(lot.getCurrentStepId());
        vo.setCurrentEqpId(lot.getCurrentEqpId());
        vo.setRouteVersionId(lot.getRouteVersionId());
        vo.setCompleted(completed);
        vo.setOffFlow(isOffFlow(lot));
        return vo;
    }

    /** 是否批次是否在off-flow */
    private static boolean isOffFlow(MesLot lot) {
        return Integer.valueOf(1).equals(lot.getOffFlow());
    }

    private static void clearOffFlow(MesLot lot) {
        lot.setOffFlow(0);
        lot.setOffFlowAnchorSort(null);
        lot.setOffFlowAnchorStepId(null);
        lot.setOffFlowAnchorEqpId(null);
        lot.setOffFlowAnchorStatus(null);
    }

    /** 写履历；extJson 放分支/回流结构化字段，remark 只留人工备注 */
    private Long writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus,
                            Integer fromSortNo, Integer toSortNo, Long stepId, Long eqpId,
                            Long routeVersionId, String remark, Long recipeId, Long recipeVersionId,
                            String extJson) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        MesTxLog log = new MesTxLog();
        log.setLotId(lot.getId());
        log.setLotNo(lot.getLotNo());
        log.setTxType(txType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromSortNo(fromSortNo);
        log.setToSortNo(toSortNo);
        log.setStepId(stepId);
        log.setEqpId(eqpId);
        log.setRecipeId(recipeId);
        log.setRecipeVersionId(recipeVersionId);
        log.setRouteVersionId(routeVersionId);
        log.setRemark(remark);
        log.setExtJson(extJson);
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
        return log.getId();
    }
}
