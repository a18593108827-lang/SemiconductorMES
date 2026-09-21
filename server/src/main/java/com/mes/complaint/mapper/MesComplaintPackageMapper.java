package com.mes.complaint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.complaint.entity.MesComplaintPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MesComplaintPackageMapper extends BaseMapper<MesComplaintPackage> {

    /**
     * 当日包号数字后缀最大值。禁止 ORDER BY package_no（无零填充时 -9 &gt; -10）。
     */
    @Select("""
            SELECT MAX(CAST(SUBSTRING_INDEX(package_no, '-', -1) AS UNSIGNED))
            FROM mes_complaint_package
            WHERE package_no LIKE CONCAT('CP-', #{ymd}, '-%')
            """)
    Long selectMaxSeqOfDay(@Param("ymd") String ymd);

    /** 占位 CAS：READY/CONTAINED 或过期 CONTAINING 写入 token */
    @Update("""
            UPDATE mes_complaint_package
            SET status = 'CONTAINING',
                contain_token = #{token},
                update_time = NOW()
            WHERE id = #{id}
              AND (
                status IN ('READY', 'CONTAINED')
                OR (status = 'CONTAINING' AND (update_time IS NULL OR update_time < #{staleBefore}))
              )
            """)
    int occupy(@Param("id") Long id,
               @Param("token") String token,
               @Param("staleBefore") LocalDateTime staleBefore);

    /** 心跳 CAS：刷新 update_time；token 不匹配则 0 */
    @Update("""
            UPDATE mes_complaint_package
            SET update_time = NOW()
            WHERE id = #{id}
              AND status = 'CONTAINING'
              AND contain_token = #{token}
            """)
    int heartbeat(@Param("id") Long id, @Param("token") String token);

    /** 结束 CAS：认 token；有成功则 CONTAINED，否则按 contain_time 回 READY/CONTAINED */
    @Update("""
            UPDATE mes_complaint_package
            SET status = CASE
                    WHEN #{markContained} = 1 THEN 'CONTAINED'
                    WHEN contain_time IS NULL THEN 'READY'
                    ELSE 'CONTAINED'
                END,
                contain_by = CASE WHEN #{markContained} = 1 THEN IFNULL(contain_by, #{uid}) ELSE contain_by END,
                contain_time = CASE WHEN #{markContained} = 1 THEN IFNULL(contain_time, NOW()) ELSE contain_time END,
                contain_token = NULL,
                update_time = NOW()
            WHERE id = #{id}
              AND status = 'CONTAINING'
              AND contain_token = #{token}
            """)
    int finish(@Param("id") Long id,
               @Param("token") String token,
               @Param("markContained") int markContained,
               @Param("uid") Long uid);
}
