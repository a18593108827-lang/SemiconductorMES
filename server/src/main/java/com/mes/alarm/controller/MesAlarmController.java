package com.mes.alarm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.alarm.dto.AlarmCodeUpdateDTO;
import com.mes.alarm.dto.AlarmQuery;
import com.mes.alarm.dto.AlarmRemarkDTO;
import com.mes.alarm.facade.AlarmFacade;
import com.mes.alarm.vo.AlarmCodeVO;
import com.mes.alarm.vo.AlarmVO;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警 HTTP：查 / 确认 / 关闭 / 码表维护。只调 AlarmFacade。
 */
@RestController
@RequestMapping("/alarm")
@RequiredArgsConstructor
public class MesAlarmController {

    private final AlarmFacade alarmFacade;

    /** 分页列表 */
    @SaCheckPermission("alarm:view")
    @GetMapping
    public R<PageResult<AlarmVO>> list(AlarmQuery query) {
        return R.ok(alarmFacade.list(query));
    }

    /** 顶栏用：未关闭的严重告警 */
    @SaCheckPermission("alarm:view")
    @GetMapping("/critical")
    public R<List<AlarmVO>> listActiveCritical() {
        return R.ok(alarmFacade.listActiveCritical());
    }

    /** 告警码表列表（含停用）；须在 /{id} 之前 */
    @SaCheckPermission("alarm:view")
    @GetMapping("/codes")
    public R<List<AlarmCodeVO>> listCodes() {
        return R.ok(alarmFacade.listCodes());
    }

    /** 更新告警码策略；不改主键 code */
    @SaCheckPermission("alarm:edit")
    @OperLog(module = "Alarm", action = "更新告警码")
    @PutMapping("/codes/{code}")
    public R<AlarmCodeVO> updateCode(@PathVariable String code, @Valid @RequestBody AlarmCodeUpdateDTO dto) {
        return R.ok(alarmFacade.updateCode(code, dto));
    }

    /** 详情 */
    @SaCheckPermission("alarm:view")
    @GetMapping("/{id}")
    public R<AlarmVO> get(@PathVariable Long id) {
        return R.ok(alarmFacade.get(id));
    }

    /** 确认：OPEN → ACK */
    @SaCheckPermission("alarm:ack")
    @OperLog(module = "Alarm", action = "确认告警")
    @PostMapping("/{id}/ack")
    public R<AlarmVO> ack(@PathVariable Long id, @RequestBody(required = false) AlarmRemarkDTO dto) {
        String remark = dto != null ? dto.getRemark() : null;
        return R.ok(alarmFacade.ack(id, remark));
    }

    /** 关闭：OPEN/ACK → CLEARED */
    @SaCheckPermission("alarm:clear")
    @OperLog(module = "Alarm", action = "关闭告警")
    @PostMapping("/{id}/clear")
    public R<AlarmVO> clear(@PathVariable Long id, @RequestBody(required = false) AlarmRemarkDTO dto) {
        String remark = dto != null ? dto.getRemark() : null;
        return R.ok(alarmFacade.clear(id, remark));
    }
}
