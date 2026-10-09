package com.mes.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.test.entity.MesTestRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MesTestRecordMapper extends BaseMapper<MesTestRecord> {

    @Select("""
            <script>
            SELECT id, record_no, lot_id, lot_no, test_stage, program_name, program_version,
                   eqp_id, eqp_code, test_time, total_qty, source_type, source_ref, remark,
                   create_by, create_time, update_time, deleted
            FROM (
                SELECT t.*, ROW_NUMBER() OVER (PARTITION BY lot_id ORDER BY test_time DESC, id DESC) AS rn
                FROM mes_test_record t
                WHERE deleted = 0 AND lot_id IN
                <foreach collection="lotIds" item="id" open="(" close=")" separator=",">
                    #{id}
                </foreach>
            ) ranked
            WHERE rn &lt;= #{limit}
            </script>
            """)
    List<MesTestRecord> selectRecentByLots(@Param("lotIds") Collection<Long> lotIds, @Param("limit") int limit);
}
