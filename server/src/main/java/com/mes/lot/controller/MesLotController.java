package com.mes.lot.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.carrier.dto.LotCarrierBindDTO;
import com.mes.carrier.facade.CarrierFacade;
import com.mes.carrier.vo.CarrierBindingVO;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.lot.dto.MesLotCreateDTO;
import com.mes.lot.dto.MesLotQuery;
import com.mes.lot.dto.MesLotUpdateDTO;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotCreateResultVO;
import com.mes.lot.vo.MesLotGenealogyNodeVO;
import com.mes.lot.vo.MesLotVO;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryTxVO;
import com.mes.hold.service.FutureHoldService;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.MesFutureHoldVO;
import com.mes.hold.vo.MesHoldVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 批次接口：创建 / 改属性 / 放行绑 Route 版本快照
 */
@RestController
@RequestMapping("/lots")
@RequiredArgsConstructor
public class MesLotController {

    private final MesLotService mesLotService;
    private final HistoryFacade historyFacade;
    private final HoldService holdService;
    private final FutureHoldService futureHoldService;
    private final CarrierFacade carrierFacade;

    /** 分页列表 */
    @SaCheckPermission("lot:list")
    @GetMapping
    public R<PageResult<MesLotVO>> page(MesLotQuery query) {
        return R.ok(mesLotService.page(query));
    }

    /** 新建批次（lotNo 空则自动生成） */
    @SaCheckPermission("lot:add")
    @OperLog(module = "Lot", action = "新建批次")
    @PostMapping
    public R<MesLotCreateResultVO> create(@Valid @RequestBody MesLotCreateDTO dto) {
        return R.ok(mesLotService.create(dto));
    }

    /** 详情（含路线 / 快照步骤） */
    @SaCheckPermission("lot:list")
    @GetMapping("/{id}")
    public R<MesLotVO> get(@PathVariable Long id) {
        return R.ok(mesLotService.get(id));
    }

    /** 本批事务履历（现场侧栏）；只读，委托 HistoryFacade，不改 Track 侧栏约定 */
    @SaCheckPermission(value = {"history:list", "track:view"}, mode = SaMode.OR)
    @GetMapping("/{id}/history")
    public R<List<HistoryTxVO>> history(@PathVariable Long id) {
        return R.ok(historyFacade.listByLot(id));
    }

    /** 某批锁批历史（含已解锁） */
    @SaCheckPermission(value = {"hold:list", "track:view"}, mode = SaMode.OR)
    @GetMapping("/{id}/holds")
    public R<List<MesHoldVO>> holds(@PathVariable Long id) {
        return R.ok(holdService.listByLot(id));
    }

    /** 某批预约锁批（含终态） */
    @SaCheckPermission(value = {"hold:list", "track:view"}, mode = SaMode.OR)
    @GetMapping("/{id}/future-holds")
    public R<List<MesFutureHoldVO>> futureHolds(@PathVariable Long id) {
        return R.ok(futureHoldService.listByLot(id));
    }

    /**
     * 谱系树（Split/Merge）
     * @param direction up|down|both，默认 both
     * @param depth 层数，默认 5
     */
    @SaCheckPermission("lot:list")
    @GetMapping("/{id}/genealogy")
    public R<MesLotGenealogyNodeVO> genealogy(@PathVariable Long id,
                                              @RequestParam(required = false, defaultValue = "both") String direction,
                                              @RequestParam(required = false) Integer depth) {
        return R.ok(mesLotService.genealogy(id, direction, depth));
    }

    /** 改属性（不含 carrierId；绑解走 Carrier API） */
    @SaCheckPermission("lot:edit")
    @OperLog(module = "Lot", action = "改批次属性")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MesLotUpdateDTO dto) {
        mesLotService.update(id, dto);
        return R.ok();
    }

    /** 绑定载具（委托 CarrierFacade） */
    @SaCheckPermission("carrier:bind")
    @OperLog(module = "Lot", action = "绑定载具")
    @PostMapping("/{id}/carrier")
    public R<CarrierBindingVO> bindCarrier(@PathVariable Long id, @Valid @RequestBody LotCarrierBindDTO dto) {
        return R.ok(carrierFacade.bind(id, dto.getCarrierRef()));
    }

    /** 解绑载具（委托 CarrierFacade） */
    @SaCheckPermission("carrier:bind")
    @OperLog(module = "Lot", action = "解绑载具")
    @DeleteMapping("/{id}/carrier")
    public R<Void> unbindCarrier(@PathVariable Long id) {
        carrierFacade.unbind(id);
        return R.ok();
    }

    /** 查本批当前绑定 */
    @SaCheckPermission(value = {"carrier:view", "lot:list"}, mode = SaMode.OR)
    @GetMapping("/{id}/carrier")
    public R<CarrierBindingVO> getCarrier(@PathVariable Long id) {
        return R.ok(carrierFacade.getBinding(id));
    }

    /** 放行（兼容入口，逻辑委托 Track） */
    @SaCheckPermission("lot:release")
    @OperLog(module = "Lot", action = "批次放行")
    @PostMapping("/{id}/release")
    public R<Void> release(@PathVariable Long id) {
        mesLotService.release(id);
        return R.ok();
    }
}
