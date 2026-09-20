package com.mes.complaint.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 追溯包 build：preview 字段 + 可选原因/备注 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ComplaintPackageBuildDTO extends ComplaintPackagePreviewDTO {

    @Size(max = 64, message = "原因码最长64")
    private String reasonCode;

    @Size(max = 512, message = "备注最长512")
    private String remark;
}
