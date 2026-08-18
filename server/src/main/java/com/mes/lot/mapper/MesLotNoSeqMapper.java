package com.mes.lot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 批次号按日流水 */
@Mapper
public interface MesLotNoSeqMapper {

    /**
     * 原子取号：首笔为 1，之后 next_no+1；借助 LAST_INSERT_ID 返回本次序号
     */
    @Insert("""
            INSERT INTO mes_lot_no_seq (seq_day, next_no)
            VALUES (#{seqDay}, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE next_no = LAST_INSERT_ID(next_no + 1)
            """)
    int bump(@Param("seqDay") String seqDay);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();
}
