package com.mes.test.mapper;

import com.mes.test.entity.MesTestSubmitGuard;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesTestSubmitGuardMapper {

    @Insert("""
            INSERT INTO mes_test_submit_guard
            (id, lot_id, eqp_key, program_name, program_version, test_time, total_qty, window_bucket)
            VALUES (#{id}, #{lotId}, #{eqpKey}, #{programName}, #{programVersion}, #{testTime}, #{totalQty},
                    FLOOR(UNIX_TIMESTAMP(NOW()) / 600))
            """)
    int insertGuard(MesTestSubmitGuard row);
}
