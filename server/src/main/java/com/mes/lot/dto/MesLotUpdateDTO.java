package com.mes.lot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 改批次属性。
 * created：可改路线；released：不可改路线与 route_version_id。
 */
@Data
public class MesLotUpdateDTO {

    private String productCode;

    @NotNull(message = "数量不能为空")
    @Min(value = 0, message = "数量不能小于0")
    private Integer qty;

    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级范围为1-100")
    @Max(value = 100, message = "优先级范围为1-100")
    private Integer priority;

    /** Hot Lot：0/1，空则保持原值；为 1 时 priority 低于 80 会抬到 80 */
    @Min(value = 0, message = "hotFlag 只能为 0 或 1")
    @Max(value = 1, message = "hotFlag 只能为 0 或 1")
    private Integer hotFlag;

    private String customerLot;

    /** 仅未放行可改；已放行传入不同值将拒绝 */
    private Long routeId;

    private String remark;
}
