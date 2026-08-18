package com.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.system.entity.SysUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Insert("INSERT INTO sys_user_role (id, user_id, role_id, create_time) VALUES (#{id}, #{userId}, 1, NOW())")
    void insertUserRole(@Param("id") Long id, @Param("userId") Long userId);
}
