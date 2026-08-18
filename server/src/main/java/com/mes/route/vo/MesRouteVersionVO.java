package com.mes.route.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 路线版本列表项 */
@Data
public class MesRouteVersionVO {
    private Long id;
    private Long routeId;
    private Integer versionNo;
    /** draft / active / archived */
    private String status;
    private LocalDateTime publishedAt;
    private Long publishedBy;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
