package com.mes.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表项
 */
@Data
public class SysUserVO {

    private Long id;
    private String userCode;
    private String userName;
    private Integer status;
    private Integer mustChangePwd;
    private List<String> roles;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
