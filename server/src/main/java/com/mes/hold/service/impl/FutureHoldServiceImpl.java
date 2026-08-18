package com.mes.hold.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.hold.dto.MesFutureHoldCreateDTO;
import com.mes.hold.dto.MesFutureHoldQuery;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.entity.MesFutureHold;
import com.mes.hold.entity.MesHoldReason;
import com.mes.hold.mapper.MesFutureHoldMapper;
import com.mes.hold.mapper.MesHoldReasonMapper;
import com.mes.hold.service.FutureHoldService;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.MesFutureHoldVO;
import com.mes.hold.vo.MesHoldVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.support.RouteEdgeResolver;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预约锁批实现。
 * pending 不改 Lot/WIP；激活调 HoldService.create 复用即时 Hold 管道。
 */
@Service
@RequiredArgsConstructor
public class FutureHoldServiceImpl implements FutureHoldService {

    /** 未生效 */
    public static final String STATUS_PENDING = "pending";
    /** 已激活（已生成 mes_hold） */
    public static final String STATUS_ACTIVATED = "activated";
    /** 已取消 */
    public static final String STATUS_CANCELLED = "cancelled";
    /** 进站前触发 */
    public static final String TIMING_PRE = "PRE";
    /** 出站落到目标站后触发 */
    public static final String TIMING_POST = "POST";
    public static final String TX_SET = "FUTURE_HOLD_SET";
    public static final String TX_CANCEL = "FUTURE_HOLD_CANCEL";
    public static final String TX_ACTIVATE = "FUTURE_HOLD_ACTIVATE";

    /** 允许设置预约的 Lot 状态 */
    private static final Set<String> SET_ALLOWED_LOT_STATUS = Set.of(
            HoldServiceImpl.STATUS_WAIT,
            HoldServiceImpl.STATUS_PROCESSING,
            HoldServiceImpl.STATUS_HELD
    );

    private final MesFutureHoldMapper mesFutureHoldMapper;
    private final MesHoldReasonMapper mesHoldReasonMapper;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final HoldService holdService;
    private final RouteEdgeResolver routeEdgeResolver;

    @Override
    public PageResult<MesFutureHoldVO> page(MesFutureHoldQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        String status = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : STATUS_PENDING;

        LambdaQueryWrapper<MesFutureHold> qw = new LambdaQueryWrapper<>();
        if (!"all".equalsIgnoreCase(status)) {
            qw.eq(MesFutureHold::getStatus, status);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            qw.like(MesFutureHold::getLotNo, query.getKeyword().trim());
        }
        if (StringUtils.hasText(query.getReasonCode())) {
            qw.eq(MesFutureHold::getReasonCode, query.getReasonCode().trim());
        }
        qw.orderByDesc(MesFutureHold::getCreateTime);

        Page<MesFutureHold> result = mesFutureHoldMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesFutureHold> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }
        return PageResult.of(toVoList(rows), result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesFutureHoldVO get(Long id) {
        MesFutureHold row = mesFutureHoldMapper.selectById(id);
        AssertUtil.notNull(row, "预约锁批不存在");
        return toVo(row, loadReasonNameMap(Set.of(row.getReasonId())));
    }

    @Override
    public List<MesFutureHoldVO> listByLot(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        List<MesFutureHold> rows = mesFutureHoldMapper.selectList(new LambdaQueryWrapper<MesFutureHold>()
                .eq(MesFutureHold::getLotId, lotId)
                .orderByDesc(MesFutureHold::getCreateTime));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return toVoList(rows);
    }

    @Override
    public List<MesFutureHoldVO> listPendingByLot(Long lotId) {
        if (lotId == null) {
            return Collections.emptyList();
        }
        List<MesFutureHold> rows = mesFutureHoldMapper.selectList(new LambdaQueryWrapper<MesFutureHold>()
                .eq(MesFutureHold::getLotId, lotId)
                .eq(MesFutureHold::getStatus, STATUS_PENDING)
                .orderByAsc(MesFutureHold::getTargetSortNo)
                .orderByAsc(MesFutureHold::getCreateTime));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return toVoList(rows);
    }

    /**
     * 设置预约：校验 Lot/站/原因 → 写 mes_future_hold(pending) → FUTURE_HOLD_SET。
     * Off-Flow 中禁止；不改 Lot.status。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesFutureHoldVO create(MesFutureHoldCreateDTO dto) {
        MesLot lot = mesLotMapper.selectById(dto.getLotId());
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(SET_ALLOWED_LOT_STATUS.contains(lot.getStatus()),
                "仅等待、加工中或锁批中的批次可预约锁批");
        AssertUtil.isFalse(Integer.valueOf(1).equals(lot.getOffFlow()), "Off-Flow 中不可预约锁批");
        AssertUtil.notNull(lot.getRouteVersionId(), "未绑定路线版本，无法预约锁批");
        AssertUtil.notNull(dto.getTargetSortNo(), "目标站序不能为空");

        String timing = normalizeTiming(dto.getTiming());
        MesRouteStep target = routeEdgeResolver.findStep(lot.getRouteVersionId(), dto.getTargetSortNo());
        AssertUtil.notNull(target, "目标站不存在于当前路线快照");
        assertForwardReachable(lot, dto.getTargetSortNo());

        MesHoldReason reason = mesHoldReasonMapper.selectOne(new LambdaQueryWrapper<MesHoldReason>()
                .eq(MesHoldReason::getReasonCode, dto.getReasonCode().trim())
                .last("LIMIT 1"));
        AssertUtil.notNull(reason, "原因码不存在");
        AssertUtil.isTrue(Objects.equals(reason.getStatus(), HoldReasonServiceImpl.STATUS_ENABLED), "原因码已停用");
        if (HoldServiceImpl.REASON_OTHER.equals(reason.getReasonCode())) {
            AssertUtil.isTrue(StringUtils.hasText(dto.getRemark()), "原因码为其它时须填写备注");
        }

        MesFutureHold dup = mesFutureHoldMapper.selectOne(new LambdaQueryWrapper<MesFutureHold>()
                .eq(MesFutureHold::getLotId, lot.getId())
                .eq(MesFutureHold::getRouteVersionId, lot.getRouteVersionId())
                .eq(MesFutureHold::getTargetSortNo, dto.getTargetSortNo())
                .eq(MesFutureHold::getTiming, timing)
                .eq(MesFutureHold::getStatus, STATUS_PENDING)
                .last("LIMIT 1"));
        AssertUtil.isTrue(dup == null, "该站该时机已存在未生效的预约锁批");

        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        String userName = user != null ? user.getUserName() : null;
        LocalDateTime now = LocalDateTime.now();

        MesFutureHold row = new MesFutureHold();
        row.setLotId(lot.getId());
        row.setLotNo(lot.getLotNo());
        row.setRouteVersionId(lot.getRouteVersionId());
        row.setTargetSortNo(dto.getTargetSortNo());
        row.setTiming(timing);
        row.setReasonId(reason.getId());
        row.setReasonCode(reason.getReasonCode());
        row.setStatus(STATUS_PENDING);
        row.setRemark(dto.getRemark());
        row.setOwnerUserId(userId);
        row.setOwnerUserName(userName);
        row.setCreateUserId(userId);
        row.setCreateUserName(userName);
        row.setCreateTime(now);
        mesFutureHoldMapper.insert(row);

        writeTxLog(lot, TX_SET, lot.getStatus(), lot.getStatus(),
                "预约锁批：站" + dto.getTargetSortNo() + " " + timing + " " + reason.getReasonName()
                        + (StringUtils.hasText(dto.getRemark()) ? "；" + dto.getRemark().trim() : ""),
                buildExt(row, null));

        return toVo(row, Map.of(reason.getId(), reason.getReasonName()));
    }

    /** 取消 pending → cancelled；已激活不可取消 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesFutureHoldVO cancel(Long id, String remark) {
        MesFutureHold row = mesFutureHoldMapper.selectById(id);
        AssertUtil.notNull(row, "预约锁批不存在");
        AssertUtil.isTrue(STATUS_PENDING.equals(row.getStatus()), "仅未生效的预约锁批可取消");

        MesLot lot = mesLotMapper.selectById(row.getLotId());
        AssertUtil.notNull(lot, "批次不存在");

        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        String userName = user != null ? user.getUserName() : null;
        LocalDateTime now = LocalDateTime.now();

        row.setStatus(STATUS_CANCELLED);
        row.setCancelUserId(userId);
        row.setCancelUserName(userName);
        row.setCancelTime(now);
        row.setCancelRemark(remark);
        int updated = mesFutureHoldMapper.updateById(row);
        AssertUtil.isTrue(updated > 0, "取消失败");

        writeTxLog(lot, TX_CANCEL, lot.getStatus(), lot.getStatus(),
                "取消预约锁批：站" + row.getTargetSortNo() + " " + row.getTiming()
                        + (StringUtils.hasText(remark) ? "；" + remark.trim() : ""),
                buildExt(row, null));

        return toVo(row, loadReasonNameMap(Set.of(row.getReasonId())));
    }

    /**
     * 到站激活：命中 pending → HoldService.create → future 行 activated。
     * 独立事务提交，避免 TrackIn 随后 assert 失败把激活一起回滚。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean tryActivate(MesLot lot, Integer sortNo, String timing) {
        if (lot == null || lot.getId() == null || sortNo == null || lot.getRouteVersionId() == null) {
            return false;
        }
        String t = normalizeTiming(timing);
        List<MesFutureHold> pending = mesFutureHoldMapper.selectList(new LambdaQueryWrapper<MesFutureHold>()
                .eq(MesFutureHold::getLotId, lot.getId())
                .eq(MesFutureHold::getRouteVersionId, lot.getRouteVersionId())
                .eq(MesFutureHold::getTargetSortNo, sortNo)
                .eq(MesFutureHold::getTiming, t)
                .eq(MesFutureHold::getStatus, STATUS_PENDING)
                .orderByAsc(MesFutureHold::getCreateTime));
        if (pending.isEmpty()) {
            return false;
        }
        AssertUtil.isTrue(pending.size() == 1, "同站同时机存在多条预约，请清理后重试");
        AssertUtil.isTrue(!holdService.hasActive(lot.getId()), "批次已锁批，无法激活预约锁批");

        MesFutureHold first = pending.get(0);
        MesHoldCreateDTO createDto = new MesHoldCreateDTO();
        createDto.setLotId(lot.getId());
        createDto.setReasonCode(first.getReasonCode());
        createDto.setRemark(first.getRemark());
        MesHoldVO holdVo = holdService.create(createDto);

        LocalDateTime now = LocalDateTime.now();
        first.setStatus(STATUS_ACTIVATED);
        first.setHoldId(holdVo.getId());
        first.setActivateTime(now);
        int rows = mesFutureHoldMapper.updateById(first);
        AssertUtil.isTrue(rows > 0, "激活预约锁批失败");

        MesLot fresh = mesLotMapper.selectById(lot.getId());
        AssertUtil.notNull(fresh, "批次不存在");
        lot.setStatus(fresh.getStatus());
        lot.setUpdateTime(fresh.getUpdateTime());

        writeTxLog(fresh, TX_ACTIVATE, HoldServiceImpl.STATUS_HELD, HoldServiceImpl.STATUS_HELD,
                "激活预约锁批：站" + sortNo + " " + t,
                buildExt(first, holdVo.getId()));
        return true;
    }

    /** 目标站须为当前站，或沿主路径 next_sort_no 前向可达 */
    private void assertForwardReachable(MesLot lot, Integer targetSortNo) {
        Integer current = lot.getCurrentSortNo();
        if (current == null) {
            return;
        }
        if (Objects.equals(current, targetSortNo)) {
            return;
        }
        AssertUtil.isTrue(targetSortNo >= current, "目标站须为当前站或之后");
        List<Integer> path = routeEdgeResolver.computeSkippedSortNos(
                lot.getRouteVersionId(), current, targetSortNo);
        AssertUtil.notNull(path, "目标站不可沿主路径前向到达");
    }

    /*
     * PRE=进站前 / POST=出站后
     */
    private String normalizeTiming(String timing) {
        if (!StringUtils.hasText(timing)) {
            return TIMING_PRE;
        }
        String t = timing.trim().toUpperCase();
        AssertUtil.isTrue(TIMING_PRE.equals(t) || TIMING_POST.equals(t), "timing 仅支持 PRE/POST");
        return t;
    }

    private String buildExt(MesFutureHold row, Long holdId) {
        JSONObject ext = new JSONObject();
        ext.set("futureHoldId", String.valueOf(row.getId()));
        ext.set("targetSortNo", row.getTargetSortNo());
        ext.set("timing", row.getTiming());
        ext.set("reasonCode", row.getReasonCode());
        if (holdId != null) {
            ext.set("holdId", String.valueOf(holdId));
        } else if (row.getHoldId() != null) {
            ext.set("holdId", String.valueOf(row.getHoldId()));
        }
        return ext.toString();
    }

    private void writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus,
                            String remark, String extJson) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        MesTxLog log = new MesTxLog();
        log.setLotId(lot.getId());
        log.setLotNo(lot.getLotNo());
        log.setTxType(txType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromSortNo(lot.getCurrentSortNo());
        log.setToSortNo(lot.getCurrentSortNo());
        log.setStepId(lot.getCurrentStepId());
        log.setEqpId(lot.getCurrentEqpId());
        log.setRouteVersionId(lot.getRouteVersionId());
        log.setRemark(remark);
        log.setExtJson(extJson);
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
    }

    private List<MesFutureHoldVO> toVoList(List<MesFutureHold> rows) {
        Set<Long> reasonIds = rows.stream().map(MesFutureHold::getReasonId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nameMap = loadReasonNameMap(reasonIds);
        return rows.stream().map(r -> toVo(r, nameMap)).toList();
    }

    private Map<Long, String> loadReasonNameMap(Set<Long> reasonIds) {
        if (reasonIds == null || reasonIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesHoldReasonMapper.selectBatchIds(reasonIds).stream()
                .collect(Collectors.toMap(MesHoldReason::getId, MesHoldReason::getReasonName, (a, b) -> a));
    }

    private MesFutureHoldVO toVo(MesFutureHold row, Map<Long, String> reasonNameMap) {
        MesFutureHoldVO vo = new MesFutureHoldVO();
        vo.setId(row.getId());
        vo.setLotId(row.getLotId());
        vo.setLotNo(row.getLotNo());
        vo.setRouteVersionId(row.getRouteVersionId());
        vo.setTargetSortNo(row.getTargetSortNo());
        vo.setTiming(row.getTiming());
        vo.setReasonId(row.getReasonId());
        vo.setReasonCode(row.getReasonCode());
        vo.setReasonName(reasonNameMap.get(row.getReasonId()));
        vo.setStatus(row.getStatus());
        vo.setHoldId(row.getHoldId());
        vo.setRemark(row.getRemark());
        vo.setOwnerUserId(row.getOwnerUserId());
        vo.setOwnerUserName(row.getOwnerUserName());
        vo.setCreateUserId(row.getCreateUserId());
        vo.setCreateUserName(row.getCreateUserName());
        vo.setCreateTime(row.getCreateTime());
        vo.setActivateTime(row.getActivateTime());
        vo.setCancelUserId(row.getCancelUserId());
        vo.setCancelUserName(row.getCancelUserName());
        vo.setCancelTime(row.getCancelTime());
        vo.setCancelRemark(row.getCancelRemark());
        return vo;
    }
}
