package com.juhai.commons.service;

import com.juhai.commons.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Administrator
* @description 针对表【t_user】的数据库操作Service
* @createDate 2023-08-09 14:52:47
*/
public interface UserService extends IService<User> {

    User getUserByName(String userName);

}
