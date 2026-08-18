package com.mes.track.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Bonus 数量调整请求 */
@Data
public class TrackBonusDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotNull(message = "调整数量不能为空")
    private Integer delta;

    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    private String remark;
}
