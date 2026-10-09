package com.mes.test.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MesTestRecordNoSeqMapper {

    @Insert("""
            INSERT INTO mes_test_record_no_seq (seq_day, next_no)
            VALUES (#{seqDay}, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE next_no = LAST_INSERT_ID(next_no + 1)
            """)
    int bump(@Param("seqDay") String seqDay);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();
}
