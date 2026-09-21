package com.mes.hold.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.common.ResultCode;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.dto.MesHoldQuery;
import com.mes.hold.entity.MesHold;
import com.mes.hold.entity.MesHoldReason;
import com.mes.hold.mapper.MesHoldMapper;
import com.mes.hold.mapper.MesHoldReasonMapper;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.HoldReasonAggVO;
import com.mes.hold.vo.MesHoldVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import com.mes.wip.service.WipProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldServiceImpl implements HoldService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RELEASED = "released";
    public static final String STATUS_WAIT = "wait";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_HELD = "held";
    public static final String TX_HOLD = "HOLD";
    public static final String TX_RELEASE_HOLD = "RELEASE_HOLD";
    public static final String REASON_OTHER = "OTHER";
    public static final String REASON_QTIME_EXCEED = "QTIME_EXCEED";
    public static final String REASON_EDC_OOS = "EDC_OOS";
    private static final String SYSTEM_USER_NAME = "SYSTEM";

    private final MesHoldMapper mesHoldMapper;
    private final MesHoldReasonMapper mesHoldReasonMapper;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final WipProjectionService wipProjectionService;

    /**
     * 看板等只要个数：active Hold 的 COUNT(*)，不分页、不拼 VO。
     */
    @Override
    public long countActive() {
        Long n = mesHoldMapper.selectCount(new LambdaQueryWrapper<MesHold>()
                .eq(MesHold::getStatus, STATUS_ACTIVE));
        return n == null ? 0L : n;
    }

    /**
     * 报表按原因聚合：以 hold_time 落入窗为准（含已释放）。
     */
    @Override
    public List<HoldReasonAggVO> summarizeByReason(LocalDate from, LocalDate toInclusive) {
        if (from == null || toInclusive == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "from / to 不能为空");
        }
        if (toInclusive.isBefore(from)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "to 不能早于 from");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = toInclusive.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<HoldReasonAggVO> rows = mesHoldMapper.aggregateByReasonInHoldTimeRange(start, endExclusive, now);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        for (HoldReasonAggVO row : rows) {
            if (row.getAvgDurationMinutes() != null && row.getAvgDurationMinutes() < 0) {
                row.setAvgDurationMinutes(0L);
            }
        }
        return rows;
    }

    @Override
    public PageResult<MesHoldVO> page(MesHoldQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();
        String status = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : STATUS_ACTIVE;

        LambdaQueryWrapper<MesHold> qw = new LambdaQueryWrapper<>();
        if (!"all".equalsIgnoreCase(status)) {
            qw.eq(MesHold::getStatus, status);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            qw.like(MesHold::getLotNo, query.getKeyword().trim());
        }
        if (StringUtils.hasText(query.getReasonCode())) {
            qw.eq(MesHold::getReasonCode, query.getReasonCode().trim());
        }
        qw.orderByDesc(MesHold::getHoldTime);

        Page<MesHold> result = mesHoldMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesHold> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }
        return PageResult.of(toVoList(rows), result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesHoldVO get(Long id) {
        MesHold row = mesHoldMapper.selectById(id);
        AssertUtil.notNull(row, "锁批记录不存在");
        return toVo(row, loadReasonNameMap(Set.of(row.getReasonId())));
    }

    @Override
    public List<MesHoldVO> listByLot(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        AssertUtil.notNull(lot, "批次不存在");
        List<MesHold> rows = mesHoldMapper.selectList(new LambdaQueryWrapper<MesHold>()
                .eq(MesHold::getLotId, lotId)
                .orderByDesc(MesHold::getHoldTime));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return toVoList(rows);
    }

    /** 批量只读：一次 IN，不重复校验 Lot 存在。调用方（ComplaintPackageAssembler）已校验。 */
    @Override
    public List<MesHoldVO> listByLots(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = lotIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<MesHold> rows = mesHoldMapper.selectList(new LambdaQueryWrapper<MesHold>()
                .in(MesHold::getLotId, ids)
                .orderByDesc(MesHold::getHoldTime)
                .orderByDesc(MesHold::getId));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return toVoList(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesHoldVO create(MesHoldCreateDTO dto) {
        MesLot lot = mesLotMapper.selectById(dto.getLotId());
        AssertUtil.notNull(lot, "批次不存在");
        // 顺序契约：先判已锁后判状态——已 held 时状态断言会先挂，MSG_ALREADY_HELD 永远到不了
        AssertUtil.isTrue(!hasActive(lot.getId()), MSG_ALREADY_HELD);
        AssertUtil.isTrue(STATUS_WAIT.equals(lot.getStatus()) || STATUS_PROCESSING.equals(lot.getStatus()),
                "仅等待加工或加工中批次可锁批");

        MesHoldReason reason = mesHoldReasonMapper.selectOne(new LambdaQueryWrapper<MesHoldReason>()
                .eq(MesHoldReason::getReasonCode, dto.getReasonCode().trim())
                .last("LIMIT 1"));
        AssertUtil.notNull(reason, "原因码不存在");
        AssertUtil.isTrue(Objects.equals(reason.getStatus(), HoldReasonServiceImpl.STATUS_ENABLED), "原因码已停用");
        if (REASON_OTHER.equals(reason.getReasonCode())) {
            AssertUtil.isTrue(StringUtils.hasText(dto.getRemark()), "原因码为其它时须填写备注");
        }

        Long userId = resolveOperUserId();
        String userName = resolveOperUserName(userId);
        LocalDateTime now = LocalDateTime.now();

        String prevStatus = lot.getStatus();
        MesHold hold = new MesHold();
        hold.setLotId(lot.getId());
        hold.setLotNo(lot.getLotNo());
        hold.setReasonId(reason.getId());
        hold.setReasonCode(reason.getReasonCode());
        hold.setStatus(STATUS_ACTIVE);
        hold.setPrevStatus(prevStatus);
        hold.setRemark(dto.getRemark());
        hold.setHoldUserId(userId);
        hold.setHoldUserName(userName);
        hold.setHoldTime(now);
        mesHoldMapper.insert(hold);

        lot.setStatus(STATUS_HELD);
        lot.setUpdateBy(userId);
        int rows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(rows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_HOLD, prevStatus, STATUS_HELD,
                "锁批：" + reason.getReasonName()
                        + (StringUtils.hasText(dto.getRemark()) ? "；" + dto.getRemark().trim() : ""));

        return toVo(hold, Map.of(reason.getId(), reason.getReasonName()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesHoldVO release(Long id, String remark) {
        MesHold hold = mesHoldMapper.selectById(id);
        AssertUtil.notNull(hold, "锁批记录不存在");
        AssertUtil.isTrue(STATUS_ACTIVE.equals(hold.getStatus()), "仅生效中的锁批可解锁");

        MesLot lot = mesLotMapper.selectById(hold.getLotId());
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_HELD.equals(lot.getStatus()), "批次当前非锁批状态");
        if (REASON_QTIME_EXCEED.equals(hold.getReasonCode()) || REASON_EDC_OOS.equals(hold.getReasonCode())) {
            AssertUtil.isTrue(StringUtils.hasText(remark),
                    REASON_EDC_OOS.equals(hold.getReasonCode()) ? "量测超规解锁须填写备注" : "Queue Time 解锁须填写备注");
        }

        Long userId = resolveOperUserId();
        String userName = resolveOperUserName(userId);
        LocalDateTime now = LocalDateTime.now();

        String restore = StringUtils.hasText(hold.getPrevStatus()) ? hold.getPrevStatus() : STATUS_WAIT;
        AssertUtil.isTrue(STATUS_WAIT.equals(restore) || STATUS_PROCESSING.equals(restore),
                "无法恢复的前置状态：" + restore);

        hold.setStatus(STATUS_RELEASED);
        hold.setReleaseRemark(remark);
        hold.setReleaseUserId(userId);
        hold.setReleaseUserName(userName);
        hold.setReleaseTime(now);
        int holdRows = mesHoldMapper.updateById(hold);
        AssertUtil.isTrue(holdRows > 0, "解锁失败");

        lot.setStatus(restore);
        lot.setUpdateBy(userId);
        int lotRows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(lotRows > 0, "数据已被他人修改，请刷新后重试");
        wipProjectionService.syncFromLot(lot);

        writeTxLog(lot, TX_RELEASE_HOLD, STATUS_HELD, restore,
                "解锁" + (StringUtils.hasText(remark) ? "：" + remark.trim() : ""));

        return toVo(hold, loadReasonNameMap(Set.of(hold.getReasonId())));
    }

    /** 原因码须存在且启用；供跨模块整单前置校验 */
    @Override
    public void assertReasonUsable(String code) {
        AssertUtil.isTrue(StringUtils.hasText(code), "原因码不存在");
        MesHoldReason reason = mesHoldReasonMapper.selectOne(new LambdaQueryWrapper<MesHoldReason>()
                .eq(MesHoldReason::getReasonCode, code.trim())
                .last("LIMIT 1"));
        AssertUtil.notNull(reason, "原因码不存在");
        AssertUtil.isTrue(Objects.equals(reason.getStatus(), HoldReasonServiceImpl.STATUS_ENABLED), "原因码已停用");
    }

    @Override
    public boolean hasActive(Long lotId) {
        return findActive(lotId) != null;
    }

    @Override
    public MesHold findActive(Long lotId) {
        if (lotId == null) {
            return null;
        }
        return mesHoldMapper.selectOne(new LambdaQueryWrapper<MesHold>()
                .eq(MesHold::getLotId, lotId)
                .eq(MesHold::getStatus, STATUS_ACTIVE)
                .orderByDesc(MesHold::getHoldTime)
                .last("LIMIT 1"));
    }

    @Override
    public void assertNoActive(Long lotId) {
        MesHold active = findActive(lotId);
        if (active == null) {
            return;
        }
        MesHoldReason reason = mesHoldReasonMapper.selectById(active.getReasonId());
        String name = reason != null ? reason.getReasonName() : active.getReasonCode();
        AssertUtil.isTrue(false, "批次已锁批：" + name);
    }

    private Long resolveOperUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String resolveOperUserName(Long userId) {
        if (userId == null) {
            return SYSTEM_USER_NAME;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getUserName() : SYSTEM_USER_NAME;
    }

    private void writeTxLog(MesLot lot, String txType, String fromStatus, String toStatus, String remark) {
        Long userId = resolveOperUserId();
        String userName = resolveOperUserName(userId);
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
        log.setOperUserId(userId);
        log.setOperUserName(userName);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
    }

    private List<MesHoldVO> toVoList(List<MesHold> rows) {
        Set<Long> reasonIds = rows.stream().map(MesHold::getReasonId).filter(Objects::nonNull).collect(Collectors.toSet());
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

    private MesHoldVO toVo(MesHold row, Map<Long, String> reasonNameMap) {
        MesHoldVO vo = new MesHoldVO();
        vo.setId(row.getId());
        vo.setLotId(row.getLotId());
        vo.setLotNo(row.getLotNo());
        vo.setReasonId(row.getReasonId());
        vo.setReasonCode(row.getReasonCode());
        vo.setReasonName(reasonNameMap.get(row.getReasonId()));
        vo.setStatus(row.getStatus());
        vo.setPrevStatus(row.getPrevStatus());
        vo.setRemark(row.getRemark());
        vo.setReleaseRemark(row.getReleaseRemark());
        vo.setHoldUserId(row.getHoldUserId());
        vo.setHoldUserName(row.getHoldUserName());
        vo.setHoldTime(row.getHoldTime());
        vo.setReleaseUserId(row.getReleaseUserId());
        vo.setReleaseUserName(row.getReleaseUserName());
        vo.setReleaseTime(row.getReleaseTime());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
