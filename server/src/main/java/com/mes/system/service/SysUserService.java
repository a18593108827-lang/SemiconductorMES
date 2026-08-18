package com.mes.system.service;

import com.mes.common.PageResult;
import com.mes.system.dto.SysUserQuery;
import com.mes.system.dto.SysUserResetPwdDTO;
import com.mes.system.dto.SysUserRoleAssignDTO;
import com.mes.system.dto.SysUserStatusDTO;
import com.mes.system.dto.SysUserUpdateDTO;
import com.mes.system.vo.SysUserVO;

import java.util.List;

/**
 * 系统用户服务
 */
public interface SysUserService {

    PageResult<SysUserVO> page(SysUserQuery query);

    void update(Long id, SysUserUpdateDTO dto);

    void updateStatus(Long id, SysUserStatusDTO dto);

    List<Long> listRoleIds(Long userId);

    void assignRoles(Long id, SysUserRoleAssignDTO dto);

    void resetPassword(Long id, SysUserResetPwdDTO dto);

    void kick(Long id);
}
