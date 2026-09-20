package com.mes.complaint.support;

import com.mes.complaint.entity.MesComplaintPackage;
import com.mes.complaint.entity.MesComplaintPackageMember;
import com.mes.complaint.mapper.MesComplaintPackageMapper;
import com.mes.complaint.mapper.MesComplaintPackageMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 唯一写入事务：包头 + 成员。禁止在此 catch UK 后继续插。
 */
@Component
@RequiredArgsConstructor
public class ComplaintPackageWriter {

    private final MesComplaintPackageMapper packageMapper;
    private final MesComplaintPackageMemberMapper memberMapper;

    /**
     * 唯一写入事务：insert 包头 + 逐条 insert 成员（≤200）。
     * 禁止在本方法内 catch UK 后继续（事务已 rollback-only）；重试由 Facade 在代理外再次调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public void insert(MesComplaintPackage header, List<MesComplaintPackageMember> members) {
        packageMapper.insert(header);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (MesComplaintPackageMember row : members) {
            row.setPackageId(header.getId());
            memberMapper.insert(row);
        }
    }
}
