package com.mes.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 追溯包 contain 入参 */
@Data
public class ComplaintPackageContainDTO {

    /** 可空；空=全成员；非空须 ⊆ 包内成员（字符串避免 JS Long 精度） */
    private List<String> lotIds;

    /** Hold 原因码，须已启用 */
    @NotBlank(message = "原因码不能为空")
    private String reasonCode;

    /** 可空；落 Hold 时会拼 [包号] */
    @Size(max = 512, message = "备注最长512")
    private String remark;
}
