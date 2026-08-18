package com.mes.route.support;

import cn.hutool.json.JSONObject;
import com.mes.route.entity.MesRouteEdge;
import lombok.Getter;

/** TrackOut 选边结果 */
@Getter
public class RouteTrackOutDecision {

    private final boolean completed;
    private final Integer toSortNo;
    private final Long toStepId;
    private final String remark;
    private final MesRouteEdge edge;
    private final String resultCode;

    public RouteTrackOutDecision(boolean completed, Integer toSortNo, Long toStepId,
                                 String remark, MesRouteEdge edge, String resultCode) {
        this.completed = completed;
        this.toSortNo = toSortNo;
        this.toStepId = toStepId;
        this.remark = remark;
        this.edge = edge;
        this.resultCode = resultCode;
    }

    public String toExtJson() {
        if (completed && edge == null) {
            return null;
        }
        JSONObject ext = new JSONObject();
        if (resultCode != null) {
            ext.set("resultCode", resultCode);
        }
        if (edge != null) {
            ext.set("edgeType", edge.getEdgeType());
            ext.set("edgeId", String.valueOf(edge.getId()));
        } else {
            ext.set("edgeType", RouteEdgeTypes.NORMAL);
        }
        if (!completed && toSortNo != null) {
            ext.set("toSortNo", toSortNo);
        }
        return ext.toString();
    }
}
