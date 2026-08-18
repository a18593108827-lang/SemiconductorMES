package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.dto.RoleUserCountRow;
import com.mes.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统角色 Mapper
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            <script>
            SELECT role_id AS roleId, COUNT(*) AS cnt
            FROM sys_user_role
            WHERE role_id IN
            <foreach collection="roleIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            GROUP BY role_id
            </script>
            """)
    List<RoleUserCountRow> selectUserCountByRoleIds(@Param("roleIds") List<Long> roleIds);
}
