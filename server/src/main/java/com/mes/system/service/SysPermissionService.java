package com.mes.system.service;

import com.mes.system.dto.SysPermCreateDTO;
import com.mes.system.dto.SysPermUpdateDTO;
import com.mes.system.vo.SysPermTreeVO;

import java.util.List;

/**
 * 权限服务
 */
public interface SysPermissionService {

    List<SysPermTreeVO> tree();

    void create(SysPermCreateDTO dto);

    void update(Long id, SysPermUpdateDTO dto);

    void delete(Long id);
}
