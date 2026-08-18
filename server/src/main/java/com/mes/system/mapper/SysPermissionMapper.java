package com.mes.system.mapper;

import com.mes.system.dto.UserRoleCodeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户权限查询
 */
@Mapper
public interface SysPermissionMapper {

    @Select("""
            SELECT DISTINCT p.perm_code
            FROM sys_permission p
            INNER JOIN sys_role_permission rp ON rp.permission_id = p.id
            INNER JOIN sys_role r ON r.id = rp.role_id AND r.status = 1 AND r.deleted = 0
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND p.status = 1 AND p.deleted = 0
              AND p.perm_code IS NOT NULL AND p.perm_code <> ''
            """)
    List<String> selectPermCodesByUserId(Long userId);

    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1 AND r.deleted = 0
            """)
    List<String> selectRoleCodesByUserId(Long userId);

    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.role_code AS roleCode
            FROM sys_user_role ur
            INNER JOIN sys_role r ON r.id = ur.role_id AND r.status = 1 AND r.deleted = 0
            WHERE ur.user_id IN
            <foreach collection="userIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<UserRoleCodeRow> selectRoleCodesByUserIds(@Param("userIds") List<Long> userIds);
}
