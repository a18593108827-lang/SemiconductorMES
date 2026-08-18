package com.mes.lot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新建批次（状态 created，未绑版本） */
@Data
public class MesLotCreateDTO {

    /**
     * 批次号（可选）。空则服务端按 LOT-yyyyMMdd-流水 自动生成；填则须唯一。
     */
    private String lotNo;

    /** 产品编码（可选） */
    private String productCode;

    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量不能小于0")
    private Integer qty;

    /** 优先级 1–100，空则默认 50 */
    @Min(value = 1, message = "优先级范围为1-100")
    @Max(value = 100, message = "优先级范围为1-100")
    private Integer priority;

    /** Hot Lot：0/1，空则 0；为 1 时 priority 低于 80 会抬到 80 */
    @Min(value = 0, message = "hotFlag 只能为 0 或 1")
    @Max(value = 1, message = "hotFlag 只能为 0 或 1")
    private Integer hotFlag;

    /** 客户侧批次号（可选） */
    private String customerLot;

    /** 预填目标路线（可选，放行前可改） */
    private Long routeId;

    private String remark;
}
