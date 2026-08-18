package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 路线版本内步骤（线性主路径：sortNo → nextSortNo）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_step")
public class MesRouteStep extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 版本 ID */
    private Long versionId;

    /** 工序 ID */
    private Long stepId;

    /** 顺序号（建议 10/20/30） */
    private Integer sortNo;

    /** 下一站顺序号；空表示结束 */
    private Integer nextSortNo;

    /** 快照设备类型（发布固化） */
    private String eqpType;

    /** 快照工序类型 */
    private Integer stepType;

    /** 快照 Skip 许可 */
    private Integer allowSkip;

    /** 快照 QueueTime */
    private Integer maxQueueMin;

    /** 本站最短加工几分钟，null=不管；太短不让出站 */
    private Integer minProcessMin;

    /** 本站最长加工几分钟，null=不管；太长先出站再锁批 */
    private Integer maxProcessMin;
}
