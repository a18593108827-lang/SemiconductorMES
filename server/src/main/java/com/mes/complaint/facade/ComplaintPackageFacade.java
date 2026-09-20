package com.mes.complaint.facade;

import com.mes.common.PageResult;
import com.mes.complaint.dto.ComplaintPackageBuildDTO;
import com.mes.complaint.dto.ComplaintPackagePreviewDTO;
import com.mes.complaint.dto.ComplaintPackageQuery;
import com.mes.complaint.vo.ComplaintPackageListVO;
import com.mes.complaint.vo.ComplaintPackagePreviewVO;
import com.mes.complaint.vo.ComplaintPackageVO;

/**
 * 客诉追溯包对外门面。
 * 编排依赖 Lot 谱系 / History / Hold / Alarm；禁止本包直查他人业务表。
 */
public interface ComplaintPackageFacade {

    /** 配置开关是否打开 */
    boolean isEnabled();

    /** 开关关则抛 COMPLAINT_PACKAGE_DISABLED */
    void assertEnabled();

    /** 影响面 preview（不落库） */
    ComplaintPackagePreviewVO preview(ComplaintPackagePreviewDTO dto);

    /** 落包头/成员后装配 VO；方法本身无事务 */
    ComplaintPackageVO build(ComplaintPackageBuildDTO dto);

    /** 详情：成员以表为准，装配块现查 */
    ComplaintPackageVO get(Long id);

    /** 包头分页摘要 */
    PageResult<ComplaintPackageListVO> page(ComplaintPackageQuery query);
}
