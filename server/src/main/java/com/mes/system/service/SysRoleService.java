package com.mes.system.service;

import com.mes.common.PageResult;
import com.mes.system.dto.SysRoleCreateDTO;
import com.mes.system.dto.SysRolePermAssignDTO;
import com.mes.system.dto.SysRoleQuery;
import com.mes.system.dto.SysRoleUpdateDTO;
import com.mes.system.vo.SysRoleVO;

import java.util.List;

/**
 * 系统角色服务
 */
public interface SysRoleService {

    PageResult<SysRoleVO> page(SysRoleQuery query);

    void create(SysRoleCreateDTO dto);

    void update(Long id, SysRoleUpdateDTO dto);

    void delete(Long id);

    List<Long> listPermissionIds(Long roleId);

    void assignPermissions(Long roleId, SysRolePermAssignDTO dto);
}
