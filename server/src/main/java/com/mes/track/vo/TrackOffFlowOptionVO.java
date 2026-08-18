package com.mes.track.vo;

import lombok.Data;

import java.util.List;

@Data
public class TrackOffFlowOptionVO {
    private Integer toSortNo;
    private String toStepCode;
    private String toStepName;
    private Integer maxOffFlowCount;
    private Integer remainCount;
    private List<String> reasonCodes;
}
