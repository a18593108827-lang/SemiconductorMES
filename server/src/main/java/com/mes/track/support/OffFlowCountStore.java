package com.mes.track.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mes.lot.entity.MesLot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Lot.off_flow_counts：{"触发站sortNo": 次数} */
@Component
public class OffFlowCountStore {

    public int get(MesLot lot, Integer fromSortNo) {
        if (fromSortNo == null || !StringUtils.hasText(lot.getOffFlowCounts())) {
            return 0;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(lot.getOffFlowCounts());
            return obj.getInt(String.valueOf(fromSortNo), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public void put(MesLot lot, Integer fromSortNo, int count) {
        JSONObject obj;
        if (StringUtils.hasText(lot.getOffFlowCounts())) {
            try {
                obj = JSONUtil.parseObj(lot.getOffFlowCounts());
            } catch (Exception e) {
                obj = new JSONObject();
            }
        } else {
            obj = new JSONObject();
        }
        obj.set(String.valueOf(fromSortNo), count);
        lot.setOffFlowCounts(obj.toString());
    }
}
