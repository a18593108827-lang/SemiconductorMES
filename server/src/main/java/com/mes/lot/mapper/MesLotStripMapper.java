package com.mes.lot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.lot.entity.MesLotStrip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MesLotStripMapper extends BaseMapper<MesLotStrip> {

    /** 多批各取前 limit 条，按 seq_no、id 升序，SQL 按批截断 */
    @Select("""
            <script>
            SELECT id, lot_id, strip_no, seq_no, die_qty, bin_code, status, remark,
                   create_by, create_time, update_time, deleted
            FROM (
                SELECT t.*, ROW_NUMBER() OVER (
                    PARTITION BY lot_id ORDER BY seq_no IS NULL, seq_no ASC, id ASC
                ) AS rn
                FROM mes_lot_strip t
                WHERE deleted = 0 AND lot_id IN
                <foreach collection="lotIds" item="id" open="(" close=")" separator=",">
                    #{id}
                </foreach>
            ) ranked
            WHERE rn &lt;= #{limit}
            </script>
            """)
    List<MesLotStrip> selectByLots(@Param("lotIds") Collection<Long> lotIds, @Param("limit") int limit);
}
