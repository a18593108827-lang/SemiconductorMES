package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户编码（登录，唯一） */
    private String userCode;

    /** 姓名（可重复） */
    private String userName;

    /** 密码（BCrypt） */
    private String password;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 1强制下次改密 */
    private Integer mustChangePwd;

    /** 账号来源 local/sso */
    private String source;

    /** 外部身份 ID（SSO） */
    private String externalId;
}
