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
import com.mes.history.vo.HistoryTxVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.track.entity.MesTxLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

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
}
