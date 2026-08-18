package com.mes.route.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 升版：基于指定版本复制为新草稿 */
@Data
public class MesRouteUpgradeDTO {

    /** 源版本 ID */
    @NotNull(message = "源版本不能为空")
    private Long fromVersionId;
}
