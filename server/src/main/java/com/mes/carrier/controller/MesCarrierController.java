package com.mes.carrier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.carrier.dto.CarrierBindDTO;
import com.mes.carrier.dto.CarrierCreateDTO;
import com.mes.carrier.dto.CarrierQuery;
import com.mes.carrier.dto.CarrierStatusDTO;
import com.mes.carrier.dto.CarrierUnbindDTO;
import com.mes.carrier.dto.CarrierUpdateDTO;
import com.mes.carrier.facade.CarrierFacade;
import com.mes.carrier.vo.CarrierBindingVO;
import com.mes.carrier.vo.CarrierVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 载具 HTTP：台账 + 绑解；只委托 CarrierFacade
 */
@RestController
@RequestMapping("/carrier")
@RequiredArgsConstructor
public class MesCarrierController {

    private final CarrierFacade carrierFacade;

    /** 分页列表 */
    @SaCheckPermission("carrier:view")
    @GetMapping
    public R<PageResult<CarrierVO>> list(CarrierQuery query) {
        return R.ok(carrierFacade.list(query));
    }

    /** 按编码查（须写在 /{id} 前，避免被当成 id） */
    @SaCheckPermission("carrier:view")
    @GetMapping("/by-code")
    public R<CarrierVO> getByCode(@RequestParam String carrierCode) {
        return R.ok(carrierFacade.getByCode(carrierCode));
    }

    /** 查 Lot 当前绑定 */
    @SaCheckPermission("carrier:view")
    @GetMapping("/binding")
    public R<CarrierBindingVO> getBinding(@RequestParam Long lotId) {
        return R.ok(carrierFacade.getBinding(lotId));
    }

    /** 详情 */
    @SaCheckPermission("carrier:view")
    @GetMapping("/{id}")
    public R<CarrierVO> get(@PathVariable Long id) {
        return R.ok(carrierFacade.get(id));
    }

    /** 新建 */
    @SaCheckPermission("carrier:edit")
    @OperLog(module = "Carrier", action = "新建载具")
    @PostMapping
    public R<CarrierVO> create(@Valid @RequestBody CarrierCreateDTO dto) {
        return R.ok(carrierFacade.create(dto));
    }

    /** 改台账（不含状态、绑定） */
    @SaCheckPermission("carrier:edit")
    @OperLog(module = "Carrier", action = "编辑载具")
    @PutMapping("/{id}")
    public R<CarrierVO> update(@PathVariable Long id, @RequestBody CarrierUpdateDTO dto) {
        return R.ok(carrierFacade.update(id, dto));
    }

    /** 改态 */
    @SaCheckPermission("carrier:edit")
    @OperLog(module = "Carrier", action = "载具改态")
    @PutMapping("/{id}/status")
    public R<CarrierVO> changeStatus(@PathVariable Long id, @Valid @RequestBody CarrierStatusDTO dto) {
        return R.ok(carrierFacade.changeStatus(id, dto.getStatus(), dto.getRemark()));
    }

    /** 绑定 */
    @SaCheckPermission("carrier:bind")
    @OperLog(module = "Carrier", action = "绑定载具")
    @PostMapping("/bind")
    public R<CarrierBindingVO> bind(@Valid @RequestBody CarrierBindDTO dto) {
        return R.ok(carrierFacade.bind(dto.getLotId(), dto.getCarrierRef()));
    }

    /** 按 Lot 解绑 */
    @SaCheckPermission("carrier:bind")
    @OperLog(module = "Carrier", action = "解绑载具")
    @PostMapping("/unbind")
    public R<Void> unbind(@Valid @RequestBody CarrierUnbindDTO dto) {
        carrierFacade.unbind(dto.getLotId());
        return R.ok();
    }

    /** 按载具解绑 */
    @SaCheckPermission("carrier:bind")
    @OperLog(module = "Carrier", action = "按载具解绑")
    @PostMapping("/{id}/unbind")
    public R<Void> unbindByCarrier(@PathVariable Long id) {
        carrierFacade.unbindByCarrier(id);
        return R.ok();
    }
}
