package com.juhai.commons.service;

import com.juhai.commons.entity.MessageText;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
* @author Administrator
* @description 针对表【t_message_text】的数据库操作Service
* @createDate 2023-08-11 15:17:41
*/
public interface MessageTextService extends IService<MessageText> {

    Map<String, MessageText> getAllMessageMap();

}
