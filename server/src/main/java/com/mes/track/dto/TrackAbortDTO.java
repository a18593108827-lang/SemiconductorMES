package com.mes.track.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加工中止请求：把正在干的批从机台上拿下来，回到本站等待 */
@Data
public class TrackAbortDTO {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    /** 为啥中止，必须在白名单里 */
    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    /** 补充说明；选「其他」时必填 */
    private String remark;
}
