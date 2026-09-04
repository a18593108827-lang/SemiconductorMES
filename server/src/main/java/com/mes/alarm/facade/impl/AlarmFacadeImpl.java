package com.mes.alarm.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.alarm.dto.AlarmQuery;
import com.mes.alarm.entity.MesAlarm;
import com.mes.alarm.facade.AlarmFacade;
import com.mes.alarm.mapper.MesAlarmMapper;
import com.mes.alarm.vo.AlarmVO;
import com.mes.alarm.ws.AlarmWsPublisher;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 告警查询 / 确认 / 关闭。鉴权在 Controller，这里只干业务。
 */
@Service
@RequiredArgsConstructor
public class AlarmFacadeImpl implements AlarmFacade {

    /** 顶栏严重告警最多拉多少条，防一次灌爆 */
    private static final int CRITICAL_LIMIT = 50;

    private final MesAlarmMapper mesAlarmMapper;
    private final AlarmWsPublisher alarmWsPublisher;

    /**
     * 分页查告警；可按状态、级别、码、最近响的时间筛；按最近响的时间倒序。
     * pageSize 上限 200，避免一次查太猛。
     */
    @Override
    public PageResult<AlarmVO> list(AlarmQuery query) {
        AlarmQuery q = query != null ? query : new AlarmQuery();
        long pageNo = q.getPage() < 1 ? 1 : q.getPage();
        long pageSize = q.getSize() < 1 ? 20 : Math.min(q.getSize(), 200);

        LambdaQueryWrapper<MesAlarm> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(MesAlarm::getStatus, q.getStatus().trim().toUpperCase());
        }
        if (StringUtils.hasText(q.getLevel())) {
            w.eq(MesAlarm::getLevel, q.getLevel().trim().toUpperCase());
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

    /** 单条详情；没有就抛「告警不存在」 */
    @Override
    public AlarmVO get(Long id) {
        return toVo(require(id));
    }

    /**
     * 确认：只能 OPEN → ACK；记下是谁、什么时候、备注。
     * 已确认或已关闭的不能再点确认。
     */
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
        return toVo(row);
    }

    /**
     * 关闭：OPEN 或 ACK → CLEARED；关完这条生命周期结束。
     * 已经关过的再关会报错。
     */
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
        return toVo(row);
    }

    /**
     * 顶栏用：还没关的严重告警（CRITICAL + OPEN/ACK），按最近响的时间倒序。
     * 一期种子码都是 WARNING，这里多半空列表，接口先占住。
     */
    @Override
    public List<AlarmVO> listActiveCritical() {
        List<MesAlarm> rows = mesAlarmMapper.selectList(new LambdaQueryWrapper<MesAlarm>()
                .eq(MesAlarm::getLevel, "CRITICAL")
                .in(MesAlarm::getStatus, MesAlarm.STATUS_OPEN, MesAlarm.STATUS_ACK)
                .orderByDesc(MesAlarm::getLastRaiseAt)
                .last("LIMIT " + CRITICAL_LIMIT));
        return rows.stream().map(this::toVo).collect(Collectors.toList());
    }

    /** 按 id 取行；id 空或不存在直接抛业务异常 */
    private MesAlarm require(Long id) {
        AssertUtil.notNull(id, "告警 id 不能为空");
        MesAlarm row = mesAlarmMapper.selectById(id);
        AssertUtil.notNull(row, "告警不存在");
        return row;
    }

    /** 当前登录人，写确认人 / 关闭人 */
    private long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 备注去空白；空串当没写 */
    private static String trimRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String s = remark.trim();
        return s.isEmpty() ? null : s;
    }

    /** 实体转给前端的 VO，字段原样搬 */
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
}
