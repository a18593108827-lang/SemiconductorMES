package com.mes.hold.service;

import com.mes.hold.vo.MesHoldReasonVO;

import java.util.List;

public interface HoldReasonService {

    /** 启用原因码（Hold 下拉） */
    List<MesHoldReasonVO> listEnabled();

    /** 全部原因码（含停用，维护用） */
    List<MesHoldReasonVO> listAll();

    void updateStatus(Long id, Integer status);
}
