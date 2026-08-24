package com.mes.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.track.entity.MesTxLog;
import org.apache.ibatis.annotations.Mapper;

/** 履历只读 Mapper，表仍是 mes_tx_log。约定禁止 insert/update/delete，写只走 Track。 */
@Mapper
public interface HistoryTxLogMapper extends BaseMapper<MesTxLog> {
}
