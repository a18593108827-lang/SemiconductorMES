package com.mes.track.vo;

import lombok.Data;

import java.util.List;

@Data
public class TrackSkipOptionVO {
    private Integer toSortNo;
    private String toStepCode;
    private String toStepName;
    private List<Integer> skippedSortNos;
    private List<String> reasonCodes;
}
