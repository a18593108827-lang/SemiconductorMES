package com.mes.track.vo;

import lombok.Data;

import java.util.List;

@Data
public class TrackReworkOptionVO {
    private Integer toSortNo;
    private String toStepCode;
    private String toStepName;
    private Integer maxReworkCount;
    private Integer remainCount;
    private List<String> reasonCodes;
}
