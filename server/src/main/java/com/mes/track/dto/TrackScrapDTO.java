package com.mes.track.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Scrap 报废请求 */
@Data
public class TrackScrapDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotNull(message = "报废数量不能为空")
    @Min(value = 1, message = "报废数量至少为1")
    private Integer scrapQty;

    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    private String remark;
}
