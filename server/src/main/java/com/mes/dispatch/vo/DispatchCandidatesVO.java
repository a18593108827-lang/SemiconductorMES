package com.mes.dispatch.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 派工候选 / 推荐结果 */
@Data
public class DispatchCandidatesVO {
    private Long lotId;
    private String lotNo;
    private String lotStatus;
    /** 当前站设备类型；空表示不过滤类型 */
    private String eqpType;
    private Long recommendedEqpId;
    /** 是否因 Hold 拦截 */
    private Boolean held;
    private String message;
    private List<DispatchCandidateItemVO> candidates = new ArrayList<>();
}
