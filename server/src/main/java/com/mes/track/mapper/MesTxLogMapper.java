package com.mes.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.track.entity.MesTxLog;
import org.apache.ibatis.annotations.Mapper;

/** Track 事务履历 Mapper */
@Mapper
public interface MesTxLogMapper extends BaseMapper<MesTxLog> {
}
