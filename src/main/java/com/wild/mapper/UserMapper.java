package com.wild.mapper;

import com.wild.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据手机号查询用户
     * @param phone
     * @return User
     */
    User queryUser(String phone);

    /**
     * 插入新用户
     * @param newUser
     */
    void insertUser(User newUser);
}
