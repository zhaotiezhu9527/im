package com.juhai.api.controller.request;

import com.juhai.commons.constants.RegConstant;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Pattern;

@Data
@ApiModel(value = "发送消息请求类", description = "发送消息请求参数")
public class SendMsgRequest {

    @Pattern(regexp = RegConstant.USER_NAME_REG, message = "validation.user.register.username")
    @ApiModelProperty(value = "发送消息的用户名", example = "dunaifen123", required = true)
    private String fromUser;

//    @Pattern(regexp = RegConstant.USER_NAME_REG, message = "validation.user.register.loginpwd")
//    @ApiModelProperty(value = "接收消息的用户名", example = "123qwe", required = true)
//    private String toUser;
}
