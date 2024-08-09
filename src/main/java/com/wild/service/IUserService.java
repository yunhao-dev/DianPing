package com.wild.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wild.dto.LoginFormDTO;
import com.wild.dto.Result;
import com.wild.entity.Blog;
import com.wild.entity.User;

import javax.servlet.http.HttpSession;

/**
 * @description: 服务类
 * @Author: yunhao_dev
 * @Date: ${DATE} ${TIME}
 *
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result saveBlog(Blog blog);
}
