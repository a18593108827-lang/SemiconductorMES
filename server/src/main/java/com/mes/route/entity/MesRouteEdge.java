package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 路线版本边（normal / rework 等） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_edge")
public class MesRouteEdge extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long versionId;
    private Integer fromSortNo;
    private Integer toSortNo;
    /** normal / branch / rework / skip_allow / off_flow / time_link */
    private String edgeType;
    private Integer maxReworkCount;
    /** Queue Time 上限分钟；空=无 */
    private Integer maxQueueMin;
    /** Queue Time 下限预留 */
    private Integer minQueueMin;
    /** HOLD / ALARM / HOLD_ALARM；空=读全局默认 */
    private String onViolate;
    /** 逗号分隔；空=任意；仅 rework */
    private String reasonCodes;
    /** branch 条件码；normal/rework 为空 */
    private String conditionCode;
    private Integer sortNo;
}
