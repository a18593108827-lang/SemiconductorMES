package com.mes.dispatch.vo;

import lombok.Data;

/** 派工候选机 */
@Data
public class DispatchCandidateItemVO {
    private Long eqpId;
    private String eqpCode;
    private String eqpName;
    private String eqpType;
    private String status;
    private String area;
    /** 排序分，越大越优先 */
    private Integer score;
    /** 推荐理由摘要 */
    private String reason;
    /** 当前绑在该机的 Lot 数（近似负载） */
    private Integer loadCount;
}
