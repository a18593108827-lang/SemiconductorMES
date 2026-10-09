package com.mes.lot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.lot.entity.MesLotCustomerMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MesLotCustomerMapMapper extends BaseMapper<MesLotCustomerMap> {

    /** 多批各取前 limit 条，按 id 升序，SQL 按批截断 */
    @Select("""
            <script>
            SELECT id, lot_id, lot_no, map_type, external_lot_no, external_source,
                   customer_code, qty, remark, create_by, create_time, deleted
            FROM (
                SELECT t.*, ROW_NUMBER() OVER (PARTITION BY lot_id ORDER BY id ASC) AS rn
                FROM mes_lot_customer_map t
                WHERE deleted = 0 AND lot_id IN
                <foreach collection="lotIds" item="id" open="(" close=")" separator=",">
                    #{id}
                </foreach>
            ) ranked
            WHERE rn &lt;= #{limit}
            </script>
            """)
    List<MesLotCustomerMap> selectByLots(@Param("lotIds") Collection<Long> lotIds, @Param("limit") int limit);
}
