package com.mes.complaint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.complaint.entity.MesComplaintPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
