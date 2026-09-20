package com.mes.complaint.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 追溯包 preview / build 共用入参（build 另加 reason/remark） */
@Data
public class ComplaintPackagePreviewDTO {

    @NotNull(message = "锚点批次不能为空")
    private Long anchorLotId;

    /** up / down / both；空=both */
    private String direction;

    /** 默认 5，上限 20 */
    private Integer depth;
}
