package com.mes.complaint.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.complaint.dto.ComplaintPackageBuildDTO;
import com.mes.complaint.dto.ComplaintPackagePreviewDTO;
import com.mes.complaint.dto.ComplaintPackageQuery;
import com.mes.complaint.facade.ComplaintPackageFacade;
import com.mes.complaint.vo.ComplaintPackageListVO;
import com.mes.complaint.vo.ComplaintPackagePreviewVO;
import com.mes.complaint.vo.ComplaintPackageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客诉追溯包 HTTP。CP-1 开关；CP-2 preview；CP-3 build / get / list。
 */
@RestController
@RequestMapping("/complaint-packages")
@RequiredArgsConstructor
public class MesComplaintPackageController {

    private final ComplaintPackageFacade complaintPackageFacade;

    /** 前端/联调用：是否开启（不因 false 抛错） */
    @SaCheckPermission("complaint:view")
    @GetMapping("/enabled")
    public R<Boolean> enabled() {
        return R.ok(complaintPackageFacade.isEnabled());
    }

    /** 分页摘要；size 上限截 100 */
    @SaCheckPermission("complaint:view")
    @GetMapping
    public R<PageResult<ComplaintPackageListVO>> page(ComplaintPackageQuery query) {
        return R.ok(complaintPackageFacade.page(query));
    }

    /** 包详情（成员以表为准，装配块现查） */
    @SaCheckPermission("complaint:view")
    @GetMapping("/{id}")
    public R<ComplaintPackageVO> get(@PathVariable Long id) {
        return R.ok(complaintPackageFacade.get(id));
    }

    /** 影响面 preview（不落库） */
    @SaCheckPermission("complaint:view")
    @PostMapping("/preview")
    public R<ComplaintPackagePreviewVO> preview(@Valid @RequestBody ComplaintPackagePreviewDTO dto) {
        return R.ok(complaintPackageFacade.preview(dto));
    }

    /** 落库 + 装配 VO */
    @SaCheckPermission("complaint:build")
    @PostMapping
    public R<ComplaintPackageVO> build(@Valid @RequestBody ComplaintPackageBuildDTO dto) {
        return R.ok(complaintPackageFacade.build(dto));
    }
}
