package com.juhai.commons.service;

import com.juhai.commons.entity.Paramter;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
* @author Administrator
* @description 针对表【t_paramter】的数据库操作Service
* @createDate 2023-08-09 14:53:21
*/
public interface ParamterService extends IService<Paramter> {

    Map<String, String> getAllParamByMap();

}
