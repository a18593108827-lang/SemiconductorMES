package com.mes.history.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mes.common.PageResult;
import com.mes.common.R;
import com.mes.history.dto.HistoryQuery;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryTxVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 履历调查查询。现场侧栏仍走 GET /lots/{id}/history，不要经 HTTP 绕一圈。
 */
@RestController
@RequestMapping("/history")
@RequiredArgsConstructor
public class MesHistoryController {

    private final HistoryFacade historyFacade;

    /** 调查分页：lotId / eqpId 二选一，可同时（该机上该批） */
    @SaCheckPermission("history:list")
    @GetMapping
    public R<PageResult<HistoryTxVO>> query(HistoryQuery query) {
        return R.ok(historyFacade.query(query));
    }

    /** 单行详情（抽屉）；没有就 404 */
    @SaCheckPermission("history:list")
    @GetMapping("/{txId}")
    public R<HistoryTxVO> get(@PathVariable Long txId) {
        return R.ok(historyFacade.getByTxId(txId));
    }
}
