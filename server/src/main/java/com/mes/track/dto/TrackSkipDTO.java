package com.mes.track.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrackSkipDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotNull(message = "跳站目标站不能为空")
    private Integer toSortNo;

    private String reasonCode;
    private String remark;
}
