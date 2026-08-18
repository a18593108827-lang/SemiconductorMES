package com.mes.system.service;

import com.mes.common.PageResult;
import com.mes.system.dto.PermRequestCreateDTO;
import com.mes.system.dto.PermRequestDecideDTO;
import com.mes.system.dto.PermRequestQuery;
import com.mes.system.vo.ApplyableRoleVO;
import com.mes.system.vo.PermRequestVO;

import java.util.List;

public interface SysPermRequestService {

    List<ApplyableRoleVO> listApplyableRoles();

    void create(PermRequestCreateDTO dto);

    PageResult<PermRequestVO> pageMine(PermRequestQuery query);

    void cancel(Long id);

    PageResult<PermRequestVO> pageTodo(PermRequestQuery query);

    PageResult<PermRequestVO> pageDone(PermRequestQuery query);

    void approve(Long id, PermRequestDecideDTO dto);

    void reject(Long id, PermRequestDecideDTO dto);
}
