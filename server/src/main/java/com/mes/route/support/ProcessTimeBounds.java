package com.mes.route.support;

import com.mes.common.AssertUtil;

/** 配工序最短/最长加工分钟时的校验：数字合法，且最短不能大于最长 */
public final class ProcessTimeBounds {

    private ProcessTimeBounds() {
    }

    public static void validate(Integer min, Integer max) {
        if (min != null) {
            AssertUtil.isTrue(min >= 1, "minProcessMin 须≥1");
        }
        if (max != null) {
            AssertUtil.isTrue(max >= 1, "maxProcessMin 须≥1");
        }
        if (min != null && max != null) {
            AssertUtil.isTrue(min <= max, "minProcessMin 不能大于 maxProcessMin");
        }
    }
}
