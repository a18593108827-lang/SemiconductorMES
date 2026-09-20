package com.mes.complaint.vo;

import com.mes.hold.vo.MesHoldVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 单 Lot Hold 装配：active / released 各截 20 */
@Data
public class ComplaintPackageLotHoldsVO {

    /** 当前生效的 Hold（最多 20） */
    private List<MesHoldVO> active = new ArrayList<>();
    /** 已释放的 Hold（最近 20） */
    private List<MesHoldVO> released = new ArrayList<>();
}
