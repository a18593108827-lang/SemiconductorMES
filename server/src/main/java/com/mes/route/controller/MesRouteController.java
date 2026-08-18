package com.mes.route.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.route.dto.MesRouteCreateDTO;
import com.mes.route.dto.MesRouteQuery;
import com.mes.route.dto.MesRouteStepsSaveDTO;
import com.mes.route.dto.MesRouteUpgradeDTO;
import com.mes.route.service.MesRouteService;
import com.mes.route.vo.MesRouteVO;
import com.mes.route.vo.MesRouteVersionDetailVO;
import com.mes.route.vo.MesRouteVersionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工艺路线 / 版本接口
 * <p>
 * 注意：{@code /versions/**} 映射放在 {@code /{id}} 之前，避免路径冲突。
 */
@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class MesRouteController {

    private final MesRouteService mesRouteService;

    /** 路线分页 */
    @SaCheckPermission("route:list")
    @GetMapping
    public R<PageResult<MesRouteVO>> page(MesRouteQuery query) {
        return R.ok(mesRouteService.page(query));
    }

    /** 新建路线（含 draft v1） */
    @SaCheckPermission("route:add")
    @OperLog(module = "Route", action = "新建路线")
    @PostMapping
    public R<Map<String, Long>> create(@Valid @RequestBody MesRouteCreateDTO dto) {
        Long id = mesRouteService.create(dto);
        Map<String, Long> data = new HashMap<>(1);
        data.put("id", id);
        return R.ok(data);
    }

    /** 版本详情 + 有序步骤 */
    @SaCheckPermission("route:list")
    @GetMapping("/versions/{versionId}")
    public R<MesRouteVersionDetailVO> getVersion(@PathVariable Long versionId) {
        return R.ok(mesRouteService.getVersion(versionId));
    }

    /** 覆盖保存草稿步骤 */
    @SaCheckPermission("route:edit")
    @OperLog(module = "Route", action = "保存草稿步骤")
    @PutMapping("/versions/{versionId}/steps")
    public R<Void> saveDraftSteps(@PathVariable Long versionId,
                                  @Valid @RequestBody MesRouteStepsSaveDTO dto) {
        mesRouteService.saveDraftSteps(versionId, dto);
        return R.ok();
    }

    /** 发布版本 */
    @SaCheckPermission("route:edit")
    @OperLog(module = "Route", action = "发布版本")
    @PostMapping("/versions/{versionId}/publish")
    public R<Void> publish(@PathVariable Long versionId) {
        mesRouteService.publish(versionId);
        return R.ok();
    }

    /** 路线详情 */
    @SaCheckPermission("route:list")
    @GetMapping("/{id}")
    public R<MesRouteVO> get(@PathVariable Long id) {
        return R.ok(mesRouteService.get(id));
    }

    /** 版本列表 */
    @SaCheckPermission("route:list")
    @GetMapping("/{id}/versions")
    public R<List<MesRouteVersionVO>> listVersions(@PathVariable Long id) {
        return R.ok(mesRouteService.listVersions(id));
    }

    /** 升版 */
    @SaCheckPermission("route:add")
    @OperLog(module = "Route", action = "升版")
    @PostMapping("/{id}/versions")
    public R<Map<String, Long>> upgrade(@PathVariable Long id,
                                        @Valid @RequestBody MesRouteUpgradeDTO dto) {
        Long versionId = mesRouteService.upgrade(id, dto);
        Map<String, Long> data = new HashMap<>(1);
        data.put("versionId", versionId);
        return R.ok(data);
    }
}
