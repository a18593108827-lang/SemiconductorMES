package com.mes.carrier.facade;

import com.mes.carrier.dto.CarrierCreateDTO;
import com.mes.carrier.dto.CarrierQuery;
import com.mes.carrier.dto.CarrierUpdateDTO;
import com.mes.carrier.vo.CarrierBindingVO;
import com.mes.carrier.vo.CarrierVO;
import com.mes.common.PageResult;

import java.util.Collection;
import java.util.Map;

/**
 * 载具对外门面。台账 + 绑解；Track 只读断言。
 */
public interface CarrierFacade {

    CarrierVO create(CarrierCreateDTO dto);

    CarrierVO update(Long id, CarrierUpdateDTO dto);

    CarrierVO changeStatus(Long id, String toStatus, String remark);

    CarrierVO get(Long id);

    CarrierVO getByCode(String carrierCode);

    PageResult<CarrierVO> list(CarrierQuery query);

    /** 绑定：carrierRef 为 id 或 carrierCode */
    CarrierBindingVO bind(Long lotId, String carrierRef);

    /** 按 Lot 解绑；未绑幂等成功 */
    void unbind(Long lotId);

    /** 按载具解绑（管理端） */
    void unbindByCarrier(Long carrierId);

    /** 根据批次id获取载具id */
    Long getCarrierId(Long lotId);

    /** 是否已绑 */
    boolean isBound(Long lotId);

    /** 进站时校验；模块关闭时不挡 */
    void assertBound(Long lotId);

    /**
     * TrackIn 扫码比对；模块关或扫码闸关时不挡。
     * 未绑 → CARRIER_REQUIRED；空扫 → CARRIER_SCAN_REQUIRED；错码 → CARRIER_MISMATCH。
     */
    void assertMatch(Long lotId, String scannedCode);

    /** 查当前绑定详情 */
    CarrierBindingVO getBinding(Long lotId);

    /** 批量解析 lotId → carrierCode；未绑不进 Map */
    Map<Long, String> resolveCodes(Collection<Long> lotIds);
}
