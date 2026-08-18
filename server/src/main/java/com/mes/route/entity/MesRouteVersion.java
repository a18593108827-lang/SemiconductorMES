package com.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工艺路线版本：draft 可编辑，active 生效，archived 归档
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_version")
public class MesRouteVersion extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 路线 ID */
    private Long routeId;

    /** 版本号（同路线内自 1 递增） */
    private Integer versionNo;

    /** 状态：draft / active / archived */
    private String status;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 发布人 user_id */
    private Long publishedBy;

    /** 备注 */
    private String remark;
}
