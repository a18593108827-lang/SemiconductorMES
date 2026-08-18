package com.mes.track.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Split 子批项 */
@Data
public class TrackSplitChildDTO {

    @NotNull(message = "子批数量不能为空")
    @Min(value = 1, message = "子批数量至少为1")
    private Integer qty;

    /** 空则自动 {parentLotNo}.{seq} */
    @Size(max = 64, message = "批次号最长64")
    private String lotNo;
}
