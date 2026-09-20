package com.mes.lot.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 谱系树展平后的名单结果 */
@Data
public class MesLotImpactFlatVO {

    /** 你点的那批 id */
    private Long anchorLotId;
    /** 你点的那批批号 */
    private String anchorLotNo;
    /** true=深度到顶了，上面/下面其实还有批没圈进来 */
    private boolean truncated;
    /** 展平后的批次名单（含锚点） */
    private List<MesLotImpactMemberVO> members = new ArrayList<>();
    /** 本次建树结果；Complaint build 复用，避免再走 genealogy() */
    private MesLotGenealogyNodeVO tree;
}
