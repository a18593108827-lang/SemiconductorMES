package com.mes.track.support;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mes.lot.entity.MesLot;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Lot.rework_counts：{"触发站sortNo": 次数} */
@Component
public class ReworkCountStore {

    public int get(MesLot lot, Integer fromSortNo) {
        if (fromSortNo == null || !StringUtils.hasText(lot.getReworkCounts())) {
            return 0;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(lot.getReworkCounts());
            return obj.getInt(String.valueOf(fromSortNo), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public void put(MesLot lot, Integer fromSortNo, int count) {
        JSONObject obj;
        if (StringUtils.hasText(lot.getReworkCounts())) {
            try {
                obj = JSONUtil.parseObj(lot.getReworkCounts());
            } catch (Exception e) {
                obj = new JSONObject();
            }
        } else {
            obj = new JSONObject();
        }
        obj.set(String.valueOf(fromSortNo), count);
        lot.setReworkCounts(obj.toString());
    }
}
