package com.juhai.commons.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName t_user
 */
@TableName(value ="t_user")
@Data
public class User implements Serializable {
    /**
     * 用户ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 用户名
     */
    @TableField(value = "user_name")
    private String userName;

    /**
     * 昵称
     */
    @TableField(value = "nick_name")
    private String nickName;

    /**
     * 登录密码
     */
    @TableField(value = "login_pwd")
    private String loginPwd;

    /**
     * 用户状态(1:正常 0:冻结)
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 注册时间
     */
    @TableField(value = "register_time")
    private Date registerTime;

    /**
     * 注册Ip
     */
    @TableField(value = "register_ip")
    private String registerIp;

    /**
     * 上次登录时间
     */
    @TableField(value = "last_time")
    private Date lastTime;

    /**
     * 上次登录IP
     */
    @TableField(value = "last_ip")
    private String lastIp;

    /**
     * 最后修改时间
     */
    @TableField(value = "modify_time")
    private Date modifyTime;

    /**
     * IM token
     */
    @TableField(value = "im_token")
    private String imToken;

    @TableField(value = "im_user_name")
    private String imUserName;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}