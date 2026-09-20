package com.mes.alarm.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.alarm.dto.AlarmCodeUpdateDTO;
import com.mes.alarm.dto.AlarmQuery;
import com.mes.alarm.entity.MesAlarm;
import com.mes.alarm.entity.MesAlarmCode;
import com.mes.alarm.facade.AlarmFacade;
import com.mes.alarm.mapper.MesAlarmCodeMapper;
import com.mes.alarm.mapper.MesAlarmMapper;
import com.mes.alarm.support.AlarmSelfHoldCodes;
import com.mes.alarm.vo.AlarmCodeVO;
import com.mes.alarm.vo.AlarmVO;
import com.mes.alarm.ws.AlarmWsPublisher;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.hold.service.HoldReasonService;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.MesHoldReasonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 告警查询 / 确认 / 关闭 / 码表维护。鉴权在 Controller，这里只干业务。
 */
@Service
@RequiredArgsConstructor
public class AlarmFacadeImpl implements AlarmFacade {

    private static final int CRITICAL_LIMIT = 50;

    private static final String LEVEL_CRITICAL = "CRITICAL";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_INFO = "INFO";
    private static final Set<String> LEVELS = Set.of(LEVEL_CRITICAL, LEVEL_WARNING, LEVEL_INFO);

    private static final String ON_RAISE_NONE = "NONE";
    private static final String ON_RAISE_HOLD_LOT = "HOLD_LOT";
    private static final Set<String> ON_RAISES = Set.of(ON_RAISE_NONE, ON_RAISE_HOLD_LOT);

    private final MesAlarmMapper mesAlarmMapper;
    private final MesAlarmCodeMapper mesAlarmCodeMapper;
    private final AlarmWsPublisher alarmWsPublisher;
    private final HoldReasonService holdReasonService;
    private final HoldService holdService;

    @Override
    public PageResult<AlarmVO> list(AlarmQuery query) {
        AlarmQuery q = query != null ? query : new AlarmQuery();
        long pageNo = q.getPage() < 1 ? 1 : q.getPage();
        long pageSize = q.getSize() < 1 ? 20 : Math.min(q.getSize(), 200);

        LambdaQueryWrapper<MesAlarm> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(MesAlarm::getStatus, q.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(q.getLevel())) {
            w.eq(MesAlarm::getLevel, q.getLevel().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(q.getCode())) {
            w.eq(MesAlarm::getCode, q.getCode().trim());
        }
        if (q.getFrom() != null) {
            w.ge(MesAlarm::getLastRaiseAt, q.getFrom());
        }
        if (q.getTo() != null) {
            w.le(MesAlarm::getLastRaiseAt, q.getTo());
        }
        w.orderByDesc(MesAlarm::getLastRaiseAt).orderByDesc(MesAlarm::getId);

        Page<MesAlarm> page = mesAlarmMapper.selectPage(new Page<>(pageNo, pageSize), w);
        List<AlarmVO> records = page.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 单条详情；附码表策略；Lot 时附是否已有生效锁批 */
    @Override
    public AlarmVO get(Long id) {
        return toDetailVo(require(id));
    }

    @Override
    @Transactional
    public AlarmVO ack(Long id, String remark) {
        MesAlarm row = require(id);
        if (!MesAlarm.STATUS_OPEN.equals(row.getStatus())) {
            throw new BusinessException("只有未确认的告警才能确认，当前状态=" + row.getStatus());
        }
        row.setStatus(MesAlarm.STATUS_ACK);
        row.setAckBy(currentUserId());
        row.setAckAt(LocalDateTime.now());
        row.setAckRemark(trimRemark(remark));
        mesAlarmMapper.updateById(row);
        alarmWsPublisher.publishAfterCommit(AlarmWsPublisher.ACTION_ACK, row);
        return toDetailVo(row);
    }

    @Override
    @Transactional
    public AlarmVO clear(Long id, String remark) {
        MesAlarm row = require(id);
        if (MesAlarm.STATUS_CLEARED.equals(row.getStatus())) {
            throw new BusinessException("告警已关闭");
        }
        if (!MesAlarm.STATUS_OPEN.equals(row.getStatus()) && !MesAlarm.STATUS_ACK.equals(row.getStatus())) {
            throw new BusinessException("当前状态不可关闭：" + row.getStatus());
        }
        row.setStatus(MesAlarm.STATUS_CLEARED);
        row.setClearBy(currentUserId());
        row.setClearAt(LocalDateTime.now());
        row.setClearRemark(trimRemark(remark));
        mesAlarmMapper.updateById(row);
        alarmWsPublisher.publishAfterCommit(AlarmWsPublisher.ACTION_CLEARED, row);
        return toDetailVo(row);
    }

    @Override
    public List<AlarmVO> listActiveCritical() {
        List<MesAlarm> rows = mesAlarmMapper.selectList(blockingCriticalBase()
                .orderByDesc(MesAlarm::getLastRaiseAt)
                .last("LIMIT " + CRITICAL_LIMIT));
        return rows.stream().map(this::toVo).collect(Collectors.toList());
    }

    /** 批次是否挂有未关闭严重告警（派工 Lot 闸） */
    @Override
    public boolean hasBlockingCriticalForLot(Long lotId) {
        if (lotId == null || lotId <= 0) {
            return false;
        }
        Long n = mesAlarmMapper.selectCount(blockingCriticalBase()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_LOT)
                .eq(MesAlarm::getEntityId, lotId)
                .last("LIMIT 1"));
        return n != null && n > 0;
    }

    /** 设备是否挂有未关闭严重告警（派工机台闸） */
    @Override
    public boolean hasBlockingCriticalForEqp(Long eqpId) {
        if (eqpId == null || eqpId <= 0) {
            return false;
        }
        Long n = mesAlarmMapper.selectCount(blockingCriticalBase()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_EQP)
                .eq(MesAlarm::getEntityId, eqpId)
                .last("LIMIT 1"));
        return n != null && n > 0;
    }

    /** 批量标出有未关闭严重告警的设备，供候选列表一次剔除 */
    @Override
    public Set<Long> listEqpIdsWithBlockingCritical(Collection<Long> eqpIds) {
        if (eqpIds == null || eqpIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = eqpIds.stream().filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<MesAlarm> rows = mesAlarmMapper.selectList(blockingCriticalBase()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_EQP)
                .in(MesAlarm::getEntityId, ids)
                .select(MesAlarm::getEntityId));
        return rows.stream()
                .map(MesAlarm::getEntityId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    /** 取批次上最近一条挡派严重告警，拼派工拒绝文案用 */
    @Override
    public AlarmVO findFirstBlockingCriticalForLot(Long lotId) {
        if (lotId == null || lotId <= 0) {
            return null;
        }
        MesAlarm row = mesAlarmMapper.selectOne(blockingCriticalBase()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_LOT)
                .eq(MesAlarm::getEntityId, lotId)
                .orderByDesc(MesAlarm::getLastRaiseAt)
                .orderByDesc(MesAlarm::getId)
                .last("LIMIT 1"));
        return row == null ? null : toVo(row);
    }

    /** 影响面摘要：Lot 集合上 OPEN+ACK 告警条数 */
    @Override
    public long countUnclearedForLots(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) {
            return 0L;
        }
        List<Long> ids = lotIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return 0L;
        }
        Long n = mesAlarmMapper.selectCount(new LambdaQueryWrapper<MesAlarm>()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_LOT)
                .in(MesAlarm::getEntityId, ids)
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK));
        return n == null ? 0L : n;
    }

    /** 影响面装配：Lot 集合上 OPEN+ACK 告警；lastRaiseAt DESC, id DESC */
    @Override
    public List<AlarmVO> listUnclearedForLots(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lotIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<MesAlarm> rows = mesAlarmMapper.selectList(new LambdaQueryWrapper<MesAlarm>()
                .eq(MesAlarm::getEntityType, MesAlarm.ENTITY_LOT)
                .in(MesAlarm::getEntityId, ids)
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK)
                .orderByDesc(MesAlarm::getLastRaiseAt)
                .orderByDesc(MesAlarm::getId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toVo).collect(Collectors.toList());
    }

    /** 挡派条件底稿：CRITICAL 且未关闭（OPEN/ACK），与顶栏一致 */
    private LambdaQueryWrapper<MesAlarm> blockingCriticalBase() {
        return new LambdaQueryWrapper<MesAlarm>()
                .eq(MesAlarm::getLevel, LEVEL_CRITICAL)
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK);
    }

    @Override
    public long countUncleared() {
        Long n = mesAlarmMapper.selectCount(new LambdaQueryWrapper<MesAlarm>()
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK));
        return n == null ? 0L : n;
    }

    @Override
    public List<AlarmVO> listUncleared(int limit) {
        int lim = limit < 1 ? 20 : Math.min(limit, 200);
        List<MesAlarm> rows = mesAlarmMapper.selectList(new LambdaQueryWrapper<MesAlarm>()
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK)
                .orderByDesc(MesAlarm::getLastRaiseAt)
                .orderByDesc(MesAlarm::getId)
                .last("LIMIT " + lim));
        return rows.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public List<AlarmCodeVO> listCodes() {
        List<MesAlarmCode> rows = mesAlarmCodeMapper.selectList(new LambdaQueryWrapper<MesAlarmCode>()
                .orderByAsc(MesAlarmCode::getCode));
        return rows.stream().map(this::toCodeVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmCodeVO updateCode(String code, AlarmCodeUpdateDTO dto) {
        AssertUtil.isTrue(StringUtils.hasText(code), "告警码不能为空");
        AssertUtil.notNull(dto, "更新告警实体不能为空");

        MesAlarmCode row = mesAlarmCodeMapper.selectById(code.trim());
        AssertUtil.notNull(row, "告警码不存在");

        String name = dto.getName() != null ? dto.getName().trim() : "";
        AssertUtil.isTrue(StringUtils.hasText(name), "名称不能为空");

        AssertUtil.isTrue(StringUtils.hasText(dto.getLevel()), "级别不能为空");
        String level = dto.getLevel().trim().toUpperCase(Locale.ROOT);
        AssertUtil.isTrue(LEVELS.contains(level), "级别只能为 CRITICAL / WARNING / INFO");

        AssertUtil.isTrue(StringUtils.hasText(dto.getOnRaise()), "策略不能为空");
        String onRaise = dto.getOnRaise().trim().toUpperCase(Locale.ROOT);
        AssertUtil.isTrue(ON_RAISES.contains(onRaise), "策略只能为 NONE / HOLD_LOT");

        Integer enabled = dto.getEnabled();
        AssertUtil.isTrue(enabled != null && (enabled == 0 || enabled == 1), "启用状态只能为 0 或 1");

        String holdReason = StringUtils.hasText(dto.getHoldReasonCode())
                ? dto.getHoldReasonCode().trim()
                : null;
        if (ON_RAISE_HOLD_LOT.equals(onRaise)) {
            AssertUtil.isTrue(!AlarmSelfHoldCodes.isSelfHold(code.trim()),
                    "该告警码已由业务自挂锁批，禁止再配 HOLD_LOT，请保持 NONE");
            AssertUtil.isTrue(StringUtils.hasText(holdReason), "HOLD_LOT 须填写锁批原因码");
            assertHoldReasonEnabled(holdReason);
        } else {
            holdReason = null;
        }

        row.setName(name);
        row.setLevel(level);
        row.setOnRaise(onRaise);
        row.setHoldReasonCode(holdReason);
        row.setEnabled(enabled);
        row.setRemark(trimRemark(dto.getRemark()));
        int n = mesAlarmCodeMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "更新失败");
        return toCodeVo(row);
    }

    /** HOLD_LOT 时：原因码须存在且启用，否则拒保存 */
    private void assertHoldReasonEnabled(String reasonCode) {
        List<MesHoldReasonVO> enabled = holdReasonService.listEnabled();
        boolean ok = enabled.stream().anyMatch(r -> reasonCode.equals(r.getReasonCode()));
        AssertUtil.isTrue(ok, "锁批原因码不存在或已停用：" + reasonCode);
    }

    private AlarmVO toDetailVo(MesAlarm row) {
        AlarmVO vo = toVo(row);
        fillCodePolicy(vo, row.getCode());
        fillLotHoldHint(vo, row);
        return vo;
    }

    private void fillCodePolicy(AlarmVO vo, String code) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        MesAlarmCode def = mesAlarmCodeMapper.selectById(code.trim());
        if (def == null) {
            return;
        }
        vo.setOnRaise(def.getOnRaise());
        vo.setHoldReasonCode(def.getHoldReasonCode());
    }

    /** 只读提示；调 HoldService，不查 hold 表 Mapper */
    private void fillLotHoldHint(AlarmVO vo, MesAlarm row) {
        if (!MesAlarm.ENTITY_LOT.equals(row.getEntityType())
                || row.getEntityId() == null
                || row.getEntityId() <= 0) {
            vo.setLotHoldActive(null);
            return;
        }
        vo.setLotHoldActive(holdService.hasActive(row.getEntityId()));
    }

    private MesAlarm require(Long id) {
        AssertUtil.notNull(id, "告警 id 不能为空");
        MesAlarm row = mesAlarmMapper.selectById(id);
        AssertUtil.notNull(row, "告警不存在");
        return row;
    }

    private long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    private static String trimRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String s = remark.trim();
        return s.isEmpty() ? null : s;
    }

    private AlarmVO toVo(MesAlarm row) {
        AlarmVO vo = new AlarmVO();
        vo.setId(row.getId());
        vo.setCode(row.getCode());
        vo.setLevel(row.getLevel());
        vo.setStatus(row.getStatus());
        vo.setMessage(row.getMessage());
        vo.setEntityType(row.getEntityType());
        vo.setEntityId(row.getEntityId());
        vo.setDedupeKey(row.getDedupeKey());
        vo.setPayloadJson(row.getPayloadJson());
        vo.setRaiseCount(row.getRaiseCount());
        vo.setFirstRaiseAt(row.getFirstRaiseAt());
        vo.setLastRaiseAt(row.getLastRaiseAt());
        vo.setAckBy(row.getAckBy());
        vo.setAckAt(row.getAckAt());
        vo.setAckRemark(row.getAckRemark());
        vo.setClearBy(row.getClearBy());
        vo.setClearAt(row.getClearAt());
        vo.setClearRemark(row.getClearRemark());
        return vo;
    }

    private AlarmCodeVO toCodeVo(MesAlarmCode row) {
        AlarmCodeVO vo = new AlarmCodeVO();
        vo.setCode(row.getCode());
        vo.setName(row.getName());
        vo.setLevel(row.getLevel());
        vo.setOnRaise(row.getOnRaise());
        vo.setHoldReasonCode(row.getHoldReasonCode());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        return vo;
    }
}
