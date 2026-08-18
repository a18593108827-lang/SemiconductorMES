package com.mes.route.service;

import com.mes.common.PageResult;
import com.mes.route.dto.MesRouteCreateDTO;
import com.mes.route.dto.MesRouteQuery;
import com.mes.route.dto.MesRouteStepsSaveDTO;
import com.mes.route.dto.MesRouteUpgradeDTO;
import com.mes.route.vo.MesRouteVO;
import com.mes.route.vo.MesRouteVersionDetailVO;
import com.mes.route.vo.MesRouteVersionVO;

import java.util.List;

/** 工艺路线 / 版本服务 */
public interface MesRouteService {

    /** 路线分页 */
    PageResult<MesRouteVO> page(MesRouteQuery query);

    /** 新建路线并生成 draft v1，返回路线 ID */
    Long create(MesRouteCreateDTO dto);

    /** 路线详情（含当前生效版本摘要） */
    MesRouteVO get(Long id);

    /** 某路线下全部版本 */
    List<MesRouteVersionVO> listVersions(Long routeId);

    /** 版本详情 + 有序步骤（Track 主依赖） */
    MesRouteVersionDetailVO getVersion(Long versionId);

    /** 覆盖保存草稿步骤；非 draft 拒绝 */
    void saveDraftSteps(Long versionId, MesRouteStepsSaveDTO dto);

    /** 发布草稿：原 active 归档，本版本变 active */
    void publish(Long versionId);

    /** 基于源版本升版，返回新 draft 版本 ID */
    Long upgrade(Long routeId, MesRouteUpgradeDTO dto);
}
