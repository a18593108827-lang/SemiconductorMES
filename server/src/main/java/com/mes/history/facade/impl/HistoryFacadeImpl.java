package com.mes.history.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.common.ResultCode;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.history.dto.HistoryQuery;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.mapper.HistoryTxLogMapper;
import com.mes.history.support.HistoryTxAssembler;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.history.vo.HistoryStepCountVO;
import com.mes.history.vo.HistoryTxVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.track.entity.MesTxLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 履历只读入口。
 * 别人只问一句：这批走过哪些事务。这里给时间线，不改状态、不替 Track 写表。
 */
@Service
@RequiredArgsConstructor
public class HistoryFacadeImpl implements HistoryFacade {

    /** 侧栏近流水上限；先取最新再翻成正序，避免长链把最近几笔切掉 */
    public static final int LOT_HISTORY_LIMIT = 500;
    public static final int QUERY_SIZE_DEFAULT = 50;
    public static final int QUERY_SIZE_MAX = 200;

    private final MesLotMapper mesLotMapper;
    private final MesEqpMapper mesEqpMapper;
    private final HistoryTxLogMapper historyTxLogMapper;
    private final HistoryTxAssembler historyTxAssembler;

    /** 先倒序截最近 500，再 reverse 成正序，现场侧栏 reverse 展示才是「最新在上」。 */
    @Override
    public List<HistoryTxVO> listByLot(Long lotId) {
        MesLot lot = mesLotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "批次不存在");
        }
        List<MesTxLog> rows = historyTxLogMapper.selectList(new LambdaQueryWrapper<MesTxLog>()
                .eq(MesTxLog::getLotId, lotId)
                .orderByDesc(MesTxLog::getCreateTime)
                .orderByDesc(MesTxLog::getId)
                .last("LIMIT " + LOT_HISTORY_LIMIT));
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(rows);
        return historyTxAssembler.toVoList(rows);
    }

    /**
     * 批量：每批最近 limit 条，时间正序。一次 IN + 窗口函数；不校验 Lot 存在。
     * Assembler 再按 history-per-lot 截更短尾端。
     */
    @Override
    public List<HistoryTxVO> listByLots(java.util.Collection<Long> lotIds, int limit) {
        if (lotIds == null || lotIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = lotIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = limit <= 0 ? LOT_HISTORY_LIMIT : Math.min(limit, LOT_HISTORY_LIMIT);
        List<MesTxLog> rows = historyTxLogMapper.selectRecentByLots(ids, cap);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        // 窗口已按 lot 内 create_time DESC, id DESC 取；分桶后翻成 ASC，与 listByLot 同口径
        Map<Long, List<MesTxLog>> byLot = new LinkedHashMap<>();
        for (MesTxLog row : rows) {
            byLot.computeIfAbsent(row.getLotId(), k -> new ArrayList<>()).add(row);
        }
        List<MesTxLog> ordered = new ArrayList<>(rows.size());
        for (List<MesTxLog> bucket : byLot.values()) {
            bucket.sort(Comparator.comparing(MesTxLog::getCreateTime)
                    .thenComparing(MesTxLog::getId));
            ordered.addAll(bucket);
        }
        return historyTxAssembler.toVoList(ordered);
    }

    /** QE 调查：新的在前。没带批次也没带设备就拒查，别把全厂履历倒给前端。 */
    @Override
    public PageResult<HistoryTxVO> query(HistoryQuery query) {
        Long lotId = query.getLotId();
        Long eqpId = query.getEqpId();
        if (lotId == null && eqpId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请指定批次或设备");
        }
        if (lotId != null && mesLotMapper.selectById(lotId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "批次不存在");
        }
        if (eqpId != null && mesEqpMapper.selectById(eqpId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }

        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? QUERY_SIZE_DEFAULT : Math.min(query.getSize(), QUERY_SIZE_MAX);

        LambdaQueryWrapper<MesTxLog> qw = new LambdaQueryWrapper<>();
        if (lotId != null) {
            qw.eq(MesTxLog::getLotId, lotId);
        }
        if (eqpId != null) {
            qw.isNotNull(MesTxLog::getEqpId).eq(MesTxLog::getEqpId, eqpId);
        }
        if (StringUtils.hasText(query.getTxType())) {
            qw.eq(MesTxLog::getTxType, query.getTxType().trim());
        }
        if (query.getFromTime() != null) {
            qw.ge(MesTxLog::getCreateTime, query.getFromTime());
        }
        if (query.getToTime() != null) {
            qw.le(MesTxLog::getCreateTime, query.getToTime());
        }
        qw.orderByDesc(MesTxLog::getCreateTime).orderByDesc(MesTxLog::getId);

        Page<MesTxLog> result = historyTxLogMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesTxLog> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }
        return PageResult.of(historyTxAssembler.toVoList(rows), result.getTotal(), pageNo, pageSize);
    }

    @Override
    public HistoryTxVO getByTxId(Long txId) {
        MesTxLog row = historyTxLogMapper.selectById(txId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "履历不存在");
        }
        return historyTxAssembler.toVoList(List.of(row)).get(0);
    }

    /**
     * 统计某事务类型每日数量
     */
    @Override
    public List<HistoryDailyCountVO> countDailyByTxType(String txType, LocalDate from, LocalDate toInclusive) {
        if (!StringUtils.hasText(txType) || from == null || toInclusive == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "txType / from / to 不能为空");
        }
        if (toInclusive.isBefore(from)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "to 不能早于 from");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = toInclusive.plusDays(1).atStartOfDay();
        List<HistoryDailyCountVO> rows = historyTxLogMapper.countDailyByTxType(
                txType.trim(), start, endExclusive);
        return rows == null ? Collections.emptyList() : rows;
    }

    /**
     * 按工序聚合统计数量；
     * 便于报表 byDay / byStep 对账。
     */
    @Override
    public List<HistoryStepCountVO> countByStepAndTxType(String txType, LocalDate from, LocalDate toInclusive) {
        if (!StringUtils.hasText(txType) || from == null || toInclusive == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "txType / from / to 不能为空");
        }
        if (toInclusive.isBefore(from)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "to 不能早于 from");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = toInclusive.plusDays(1).atStartOfDay();
        List<HistoryStepCountVO> rows = historyTxLogMapper.countByStepAndTxType(
                txType.trim(), start, endExclusive);
        return rows == null ? Collections.emptyList() : rows;
    }
}
