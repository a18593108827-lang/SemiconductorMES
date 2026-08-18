package com.mes.auth.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录用户信息
 */
@Data
public class UserInfoVO {

    private Long id;
    private String userCode;
    private String userName;
    private Integer mustChangePwd;
    private List<String> roles = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    /** 侧栏菜单树（目录+菜单，已按权限过滤） */
    private List<MenuVO> menus = new ArrayList<>();

    @Data
    public static class MenuVO {
        private Long id;
        private Long parentId;
        private Integer permType;
        private String permCode;
        private String permName;
        private String path;
        private String icon;
        private Integer sortNo;
        private List<MenuVO> children = new ArrayList<>();
    }
}
