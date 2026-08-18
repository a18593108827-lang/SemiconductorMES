package com.mes.route.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 路线版本详情（含有序步骤，供 Track 校验下一站）
 */
@Data
public class MesRouteVersionDetailVO {
    private Long id;
    private Long routeId;
    private String routeCode;
    private String routeName;
    private Integer versionNo;
    /** draft / active / archived */
    private String status;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    private String remark;
    /** 按 sortNo 升序 */
    private List<MesRouteStepVO> steps;
    /** 边（含 rework） */
    private List<MesRouteEdgeVO> edges;
}
