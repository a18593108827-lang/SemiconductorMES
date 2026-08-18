package com.mes.dispatch.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.dispatch.dto.DispatchReserveCreateDTO;
import com.mes.dispatch.entity.MesDispatchReserve;
import com.mes.dispatch.mapper.MesDispatchReserveMapper;
import com.mes.dispatch.service.DispatchService;
import com.mes.dispatch.vo.DispatchCandidateItemVO;
import com.mes.dispatch.vo.DispatchCandidatesVO;
import com.mes.dispatch.vo.DispatchReserveVO;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.equipment.service.MesEqpService;
import com.mes.equipment.service.impl.MesEqpServiceImpl;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.recipe.facade.RecipeFacade;
import com.mes.route.support.StepEqpTypeGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    public static final String RESERVE_ACTIVE = "active";
    public static final String RESERVE_RELEASED = "released";
    public static final String RESERVE_EXPIRED = "expired";
    public static final String RESERVE_CONSUMED = "consumed";

    private static final Set<String> LOAD_STATUSES = Set.of("wait", "processing", "held");
    private static final Set<String> RESERVE_LOT_STATUSES = Set.of("wait", "processing");

    private final MesLotMapper mesLotMapper;
    private final MesEqpMapper mesEqpMapper;
    private final MesDispatchReserveMapper mesDispatchReserveMapper;
    private final HoldService holdService;
    private final MesEqpService mesEqpService;
    private final RecipeFacade recipeFacade;
    private final StepEqpTypeGuard stepEqpTypeGuard;

    /** 预约超时分钟数，默认 30 */
    @Value("${mes.dispatch.reserve-ttl-minutes:30}")
    private int reserveTtlMinutes;

    @Override
    public DispatchCandidatesVO listCandidates(Long lotId) {
        AssertUtil.notNull(lotId, "lotId不能为空");
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");

        DispatchCandidatesVO vo = new DispatchCandidatesVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setLotStatus(lot.getStatus());

        String eqpType = stepEqpTypeGuard.resolveRequired(lot);
        vo.setEqpType(eqpType);

        if (holdService.hasActive(lotId)) {
            vo.setHeld(true);
            vo.setMessage("批次已锁批，不可派工");
            vo.setCandidates(List.of());
            vo.setRecommendedEqpId(null);
            return vo;
        }
        vo.setHeld(false);

        LambdaQueryWrapper<MesEqp> qw = new LambdaQueryWrapper<>();
        qw.eq(MesEqp::getEnabled, MesEqpServiceImpl.ENABLED)
                .in(MesEqp::getStatus, MesEqpServiceImpl.STATUS_IDLE, MesEqpServiceImpl.STATUS_RUNNING);
        if (StringUtils.hasText(eqpType)) {
            qw.eq(MesEqp::getEqpType, eqpType);
        }
        List<MesEqp> rows = mesEqpMapper.selectList(qw);
        if (rows.isEmpty()) {
            vo.setMessage("无可用设备");
            vo.setCandidates(List.of());
            return vo;
        }

        Set<Long> blockedEqpIds = loadBlockedEqpIds(lotId);
        blockedEqpIds.addAll(loadOffFlowAnchorEqpIds(lotId));
        Map<Long, Integer> loadMap = loadCounts(rows.stream().map(MesEqp::getId).collect(Collectors.toList()));

        List<Long> eqpIds = rows.stream().map(MesEqp::getId).collect(Collectors.toList());
        Set<Long> qualified = new HashSet<>(recipeFacade.listQualifiedEqpIds(lot.getCurrentStepId(), eqpIds));

        List<DispatchCandidateItemVO> items = new ArrayList<>();
        for (MesEqp row : rows) {
            if (blockedEqpIds.contains(row.getId())) {
                continue;
            }
            if (!qualified.contains(row.getId())) {
                continue;
            }
            int load = loadMap.getOrDefault(row.getId(), 0);
            boolean idle = MesEqpServiceImpl.STATUS_IDLE.equals(row.getStatus());
            int score = (idle ? 100 : 50) - Math.min(load, 5) * 10;

            DispatchCandidateItemVO item = new DispatchCandidateItemVO();
            item.setEqpId(row.getId());
            item.setEqpCode(row.getEqpCode());
            item.setEqpName(row.getEqpName());
            item.setEqpType(row.getEqpType());
            item.setStatus(row.getStatus());
            item.setArea(row.getArea());
            item.setLoadCount(load);
            item.setScore(score);
            item.setReason(buildReason(idle, load));
            items.add(item);
        }

        items.sort(Comparator
                .comparing((DispatchCandidateItemVO i) -> !MesEqpServiceImpl.STATUS_IDLE.equals(i.getStatus()))
                .thenComparingInt(i -> i.getLoadCount() == null ? 0 : i.getLoadCount())
                .thenComparing(DispatchCandidateItemVO::getEqpCode, Comparator.nullsLast(String::compareTo)));

        vo.setCandidates(items);
        if (!items.isEmpty()) {
            vo.setRecommendedEqpId(items.get(0).getEqpId());
        } else if (rows.isEmpty()) {
            vo.setMessage("无可用设备");
        } else if (!blockedEqpIds.isEmpty() && qualified.size() == eqpIds.size()) {
            vo.setMessage("无可用设备（均被预约占用）");
        } else {
            vo.setMessage("无可用设备（含配方资格过滤）");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchReserveVO reserve(DispatchReserveCreateDTO dto) {
        AssertUtil.notNull(dto.getLotId(), "lotId不能为空");
        AssertUtil.notNull(dto.getEqpId(), "eqpId不能为空");

        MesLot lot = mesLotMapper.selectById(dto.getLotId());
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(RESERVE_LOT_STATUSES.contains(lot.getStatus()), "仅 wait/processing 可预约设备");
        holdService.assertNoActive(lot.getId());

        mesEqpService.assertUsable(dto.getEqpId());
        assertNotOffFlowAnchored(dto.getEqpId(), lot.getId());
        MesEqp eqp = mesEqpMapper.selectById(dto.getEqpId());
        AssertUtil.notNull(eqp, "设备不存在");
        stepEqpTypeGuard.requireMatch(lot, dto.getEqpId());

        // 检查预约是否过期（写路径加行锁）
        MesDispatchReserve mine = activeOrNull(findActiveByLot(lot.getId(), true));
        MesDispatchReserve otherOnEqp = activeOrNull(findActiveByEqp(eqp.getId(), true));
        // 如果设备被其他批次预约，则不允许预约
        AssertUtil.isTrue(otherOnEqp == null || Objects.equals(otherOnEqp.getLotId(), lot.getId()),
                "设备已被其他批次预约：" + eqp.getEqpCode());

        // 当前设备id和批次id一致，则直接返回
        if (mine != null && Objects.equals(mine.getEqpId(), eqp.getId())) {
            return toReserveVo(mine, lot, eqp);
        }
        // 释放当前批次的预约（必须清 slot，否则同批唯一键挡住新约）
        if (mine != null) {
            leaveActive(mine, RESERVE_RELEASED,
                    StringUtils.hasText(mine.getRemark()) ? mine.getRemark() : "改约自动释约", null);
        }

        long userId = StpUtil.getLoginIdAsLong();
        int ttl = reserveTtlMinutes > 0 ? reserveTtlMinutes : 30;
        MesDispatchReserve row = new MesDispatchReserve();
        row.setLotId(lot.getId());
        row.setLotSlot(lot.getId());
        row.setEqpId(eqp.getId());
        row.setEqpSlot(eqp.getId());
        row.setStatus(RESERVE_ACTIVE);
        row.setExpireTime(LocalDateTime.now().plusMinutes(ttl));
        row.setReserveUserId(userId);
        row.setRemark(blankToNull(dto.getRemark()));
        try {
            mesDispatchReserveMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw duplicateReserveError(e, eqp.getEqpCode());
        }
        return toReserveVo(row, lot, eqp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DispatchReserveVO release(Long id, String remark) {
        AssertUtil.notNull(id, "预约ID不能为空");
        MesDispatchReserve row = mesDispatchReserveMapper.selectOne(new LambdaQueryWrapper<MesDispatchReserve>()
                .eq(MesDispatchReserve::getId, id)
                .last("FOR UPDATE"));
        AssertUtil.notNull(row, "预约不存在");
        expireIfNeeded(row);
        AssertUtil.isTrue(RESERVE_ACTIVE.equals(row.getStatus()), "仅生效中的预约可释约");

        String finalRemark = StringUtils.hasText(remark) ? remark.trim() : row.getRemark();
        leaveActive(row, RESERVE_RELEASED, finalRemark, null);
        AssertUtil.isTrue(RESERVE_RELEASED.equals(row.getStatus()), "预约状态已变更，请刷新");

        MesLot lot = mesLotMapper.selectById(row.getLotId());
        MesEqp eqp = mesEqpMapper.selectById(row.getEqpId());
        return toReserveVo(row, lot, eqp);
    }

    @Override
    public List<DispatchReserveVO> listReserves(Long lotId, Long eqpId, String status) {
        AssertUtil.isTrue(lotId != null || eqpId != null, "lotId 与 eqpId 至少传一个");

        LambdaQueryWrapper<MesDispatchReserve> qw = new LambdaQueryWrapper<>();
        if (lotId != null) {
            expireIfNeeded(findActiveByLot(lotId, false));
            qw.eq(MesDispatchReserve::getLotId, lotId);
        }
        if (eqpId != null) {
            expireIfNeeded(findActiveByEqp(eqpId, false));
            qw.eq(MesDispatchReserve::getEqpId, eqpId);
        }
        String st = StringUtils.hasText(status) ? status.trim() : RESERVE_ACTIVE;
        qw.eq(MesDispatchReserve::getStatus, st);
        qw.orderByDesc(MesDispatchReserve::getCreateTime);

        List<MesDispatchReserve> rows = mesDispatchReserveMapper.selectList(qw);
        List<DispatchReserveVO> list = new ArrayList<>(rows.size());
        for (MesDispatchReserve row : rows) {
            MesLot lot = mesLotMapper.selectById(row.getLotId());
            MesEqp eqp = mesEqpMapper.selectById(row.getEqpId());
            list.add(toReserveVo(row, lot, eqp));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assertReserveMatch(Long lotId, Long eqpId) {
        if (lotId == null) {
            return;
        }
        MesDispatchReserve active = activeOrNull(findActiveByLot(lotId, true));
        if (active == null) {
            return;
        }
        AssertUtil.isTrue(eqpId != null && Objects.equals(active.getEqpId(), eqpId),
                "已预约其他设备，请按预约机台开工或先释约");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeOnTrackIn(Long lotId, Long eqpId, Long txLogId) {
        if (lotId == null) {
            return;
        }
        MesDispatchReserve active = activeOrNull(findActiveByLot(lotId, true));
        if (active == null) {
            return;
        }
        if (eqpId != null && !Objects.equals(active.getEqpId(), eqpId)) {
            return;
        }
        leaveActive(active, RESERVE_CONSUMED, null, txLogId);
    }

    @Override
    public void assertNotOffFlowAnchored(Long eqpId, Long excludeLotId) {
        if (eqpId == null) {
            return;
        }
        LambdaQueryWrapper<MesLot> qw = new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getOffFlow, 1)
                .eq(MesLot::getOffFlowAnchorEqpId, eqpId)
                .eq(MesLot::getOffFlowAnchorStatus, "processing");
        if (excludeLotId != null) {
            qw.ne(MesLot::getId, excludeLotId);
        }
        MesLot holder = mesLotMapper.selectOne(qw.last("LIMIT 1"));
        if (holder == null) {
            return;
        }
        throw new BusinessException("设备被 Off-Flow 批次占用：" + holder.getLotNo());
    }

    /**
     * Abort 防御释约：开工成功后预约通常已经是 consumed，这里多半啥也不干。
     * 万一还有 active，翻成 released，备注记 ABORT，方便对账。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long releaseActiveOnAbort(Long lotId, Long abortTxId) {
        if (lotId == null) {
            return null;
        }
        MesDispatchReserve active = activeOrNull(findActiveByLot(lotId, true));
        if (active == null) {
            return null;
        }
        Long reserveId = active.getId();
        // 不写 consume_tx_id（那是开工消费用的）；备注挂上 Abort 履历 id 方便查
        String remark = abortTxId == null ? "ABORT" : "ABORT tx=" + abortTxId;
        leaveActive(active, RESERVE_RELEASED, remark, null);
        return reserveId;
    }

    // 获取当前批次的 active 预约；forUpdate=true 时行锁（须在事务内）
    private MesDispatchReserve findActiveByLot(Long lotId, boolean forUpdate) {
        if (lotId == null) {
            return null;
        }
        LambdaQueryWrapper<MesDispatchReserve> qw = new LambdaQueryWrapper<MesDispatchReserve>()
                .eq(MesDispatchReserve::getLotId, lotId)
                .eq(MesDispatchReserve::getStatus, RESERVE_ACTIVE)
                .orderByDesc(MesDispatchReserve::getCreateTime)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return mesDispatchReserveMapper.selectOne(qw);
    }

    // 获取当前设备的 active 预约；forUpdate=true 时行锁（须在事务内）
    private MesDispatchReserve findActiveByEqp(Long eqpId, boolean forUpdate) {
        if (eqpId == null) {
            return null;
        }
        LambdaQueryWrapper<MesDispatchReserve> qw = new LambdaQueryWrapper<MesDispatchReserve>()
                .eq(MesDispatchReserve::getEqpId, eqpId)
                .eq(MesDispatchReserve::getStatus, RESERVE_ACTIVE)
                .orderByDesc(MesDispatchReserve::getCreateTime)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return mesDispatchReserveMapper.selectOne(qw);
    }


    // 返回 active 预约
    private MesDispatchReserve activeOrNull(MesDispatchReserve row) {
        expireIfNeeded(row);
        if (row == null || !RESERVE_ACTIVE.equals(row.getStatus())) {
            return null;
        }
        return row;
    }

    // 检查是否过期（退出 active 时清空 slot）
    private void expireIfNeeded(MesDispatchReserve row) {
        if (row == null || !RESERVE_ACTIVE.equals(row.getStatus())) {
            return;
        }
        if (row.getExpireTime() != null && row.getExpireTime().isBefore(LocalDateTime.now())) {
            leaveActive(row, RESERVE_EXPIRED, null, null);
        }
    }

    /**
     * active → 终态：清 eqp_slot/lot_slot，腾出唯一坑；带 version 条件。
     * 必须用 UpdateWrapper，updateById 默认不写 null；Wrapper 路径需手写 version。
     */
    private void leaveActive(MesDispatchReserve row, String toStatus, String remark, Long consumeTxId) {
        int ver = row.getVersion() == null ? 0 : row.getVersion();
        LambdaUpdateWrapper<MesDispatchReserve> uw = new LambdaUpdateWrapper<>();
        uw.eq(MesDispatchReserve::getId, row.getId())
                .eq(MesDispatchReserve::getStatus, RESERVE_ACTIVE)
                .eq(MesDispatchReserve::getVersion, ver)
                .set(MesDispatchReserve::getStatus, toStatus)
                .set(MesDispatchReserve::getEqpSlot, null)
                .set(MesDispatchReserve::getLotSlot, null)
                .set(MesDispatchReserve::getVersion, ver + 1);
        if (remark != null) {
            uw.set(MesDispatchReserve::getRemark, remark);
        }
        if (consumeTxId != null) {
            uw.set(MesDispatchReserve::getConsumeTxId, consumeTxId);
        }
        int n = mesDispatchReserveMapper.update(null, uw);
        if (n > 0) {
            row.setStatus(toStatus);
            row.setEqpSlot(null);
            row.setLotSlot(null);
            row.setVersion(ver + 1);
            if (remark != null) {
                row.setRemark(remark);
            }
            if (consumeTxId != null) {
                row.setConsumeTxId(consumeTxId);
            }
        } else {
            MesDispatchReserve fresh = mesDispatchReserveMapper.selectById(row.getId());
            if (fresh != null) {
                row.setStatus(fresh.getStatus());
                row.setEqpSlot(fresh.getEqpSlot());
                row.setLotSlot(fresh.getLotSlot());
                row.setRemark(fresh.getRemark());
                row.setConsumeTxId(fresh.getConsumeTxId());
                row.setVersion(fresh.getVersion());
            }
        }
    }

    private static BusinessException duplicateReserveError(DuplicateKeyException e, String eqpCode) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (msg == null) {
            msg = "";
        }
        if (msg.contains("uk_reserve_eqp_slot")) {
            return new BusinessException("设备已被其他批次预约：" + eqpCode);
        }
        if (msg.contains("uk_reserve_lot_slot")) {
            return new BusinessException("批次已有有效预约，请刷新后重试");
        }
        return new BusinessException("预约冲突，请刷新后重试");
    }

    private Set<Long> loadBlockedEqpIds(Long lotId) {
        LocalDateTime now = LocalDateTime.now();
        List<MesDispatchReserve> reserves = mesDispatchReserveMapper.selectList(
                new LambdaQueryWrapper<MesDispatchReserve>()
                        .eq(MesDispatchReserve::getStatus, RESERVE_ACTIVE)
                        .gt(MesDispatchReserve::getExpireTime, now)
                        .ne(MesDispatchReserve::getLotId, lotId));
        Set<Long> set = new HashSet<>();
        for (MesDispatchReserve r : reserves) {
            if (r.getEqpId() != null) {
                set.add(r.getEqpId());
            }
        }
        return set;
    }

    /** 其他批 processing 进 Off-Flow 后锚点机台，候选列表剔除 */
    private Set<Long> loadOffFlowAnchorEqpIds(Long lotId) {
        LambdaQueryWrapper<MesLot> qw = new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getOffFlow, 1)
                .eq(MesLot::getOffFlowAnchorStatus, "processing")
                .isNotNull(MesLot::getOffFlowAnchorEqpId);
        if (lotId != null) {
            qw.ne(MesLot::getId, lotId);
        }
        List<MesLot> rows = mesLotMapper.selectList(qw);
        Set<Long> set = new HashSet<>();
        for (MesLot row : rows) {
            if (row.getOffFlowAnchorEqpId() != null) {
                set.add(row.getOffFlowAnchorEqpId());
            }
        }
        return set;
    }

    private Map<Long, Integer> loadCounts(List<Long> eqpIds) {
        if (eqpIds == null || eqpIds.isEmpty()) {
            return Map.of();
        }
        List<MesLot> lots = mesLotMapper.selectList(new LambdaQueryWrapper<MesLot>()
                .in(MesLot::getCurrentEqpId, eqpIds)
                .in(MesLot::getStatus, LOAD_STATUSES));
        Map<Long, Integer> map = new HashMap<>();
        for (MesLot lot : lots) {
            if (lot.getCurrentEqpId() == null) {
                continue;
            }
            map.merge(lot.getCurrentEqpId(), 1, Integer::sum);
        }
        // Off-Flow 锚点机台仍计负载（对齐 CM 逻辑占台）
        List<MesLot> anchors = mesLotMapper.selectList(new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getOffFlow, 1)
                .eq(MesLot::getOffFlowAnchorStatus, "processing")
                .in(MesLot::getOffFlowAnchorEqpId, eqpIds));
        for (MesLot lot : anchors) {
            if (lot.getOffFlowAnchorEqpId() == null) {
                continue;
            }
            map.merge(lot.getOffFlowAnchorEqpId(), 1, Integer::sum);
        }
        return map;
    }

    private DispatchReserveVO toReserveVo(MesDispatchReserve row, MesLot lot, MesEqp eqp) {
        DispatchReserveVO vo = new DispatchReserveVO();
        vo.setId(row.getId());
        vo.setLotId(row.getLotId());
        vo.setLotNo(lot != null ? lot.getLotNo() : null);
        vo.setEqpId(row.getEqpId());
        vo.setEqpCode(eqp != null ? eqp.getEqpCode() : null);
        vo.setEqpName(eqp != null ? eqp.getEqpName() : null);
        vo.setStatus(row.getStatus());
        vo.setExpireTime(row.getExpireTime());
        vo.setReserveUserId(row.getReserveUserId());
        vo.setConsumeTxId(row.getConsumeTxId());
        vo.setRemark(row.getRemark());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    private static String buildReason(boolean idle, int load) {
        String statusPart = idle ? "空闲" : "运行中";
        String loadPart = load <= 0 ? "低负载" : ("负载" + load);
        return statusPart + " · " + loadPart;
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }
}
