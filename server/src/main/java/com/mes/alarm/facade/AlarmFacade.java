package com.mes.alarm.facade;

import com.mes.common.PageResult;
import com.mes.alarm.dto.AlarmQuery;
import com.mes.alarm.vo.AlarmVO;

import java.util.List;

/**
 * 告警查询与人工处置（确认 / 关闭）。
 * raise 仍走 AlarmService；本门面不鉴权，鉴权在 Controller。
 */
public interface AlarmFacade {

    /** 分页列表；可按状态 / 级别 / 码 / 时间筛 */
    PageResult<AlarmVO> list(AlarmQuery query);

    /** 详情；没有抛业务异常 */
    AlarmVO get(Long id);

    /** OPEN → ACK */
    AlarmVO ack(Long id, String remark);

    /** OPEN 或 ACK → CLEARED */
    AlarmVO clear(Long id, String remark);

    /** 顶栏：未关闭的严重告警（OPEN/ACK + CRITICAL） */
    List<AlarmVO> listActiveCritical();
}
