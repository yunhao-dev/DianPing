package com.wild.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wild.dto.LoginFormDTO;
import com.wild.dto.Result;
import com.wild.dto.UserDTO;
import com.wild.entity.Blog;
import com.wild.entity.Follow;
import com.wild.entity.User;
import com.wild.mapper.FollowMapper;
import com.wild.mapper.UserMapper;
import com.wild.service.IBlogService;
import com.wild.service.IUserService;
import com.wild.utils.RegexUtils;
import com.wild.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.wild.utils.RedisConstants.*;
import static com.wild.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IBlogService blogService;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private RedissonClient redisson;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        RLock lock = redisson.getLock(LOCK_CODE_KEY + phone);
        try {
            lock.lock();
            // 1.校验手机号
            if(RegexUtils.isPhoneInvalid(phone)) {
                // 2.如果不符合，返回错误信息
                return Result.fail("手机号格式错误");
            }
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            if(StrUtil.isNotBlank(cacheCode)){
                return Result.fail("请勿频繁发送验证码");
            }
            // 3.符合，生成验证码
            String code = RandomUtil.randomNumbers(6);
            // 4.保存验证码到redis
            stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
            // 5.发送验证码
            log.debug("发送短信验证码成功，验证码：{}",code);
            // 6.返回
            return Result.ok();
        }finally {
            lock.unlock();
        }

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        // 2.校验验证码
        String code = loginForm.getCode();
        // 3.不一致，报错
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }
        // 4.一致，根据手机号查询用户
        User user = userMapper.queryUser(phone);
        // 5. 判断用户是否存在
        if(user == null){
            // 6.不存在，创建新用户并保存
            User newUser = new User();
            newUser.setPhone(phone);
            newUser.setCreateTime(LocalDateTime.now());
            newUser.setUpdateTime(LocalDateTime.now());
            newUser.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
            userMapper.insertUser(newUser);
        }

        // 7.保存用户信息到redis中
        // 7.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2 将User对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4 设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?

        List<Follow> follows =  followMapper.queryAllFans(user.getId());
        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1. 获取粉丝Id
            Long userId = follow.getUserId();
            // 4.2. 推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 2.拼接Key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)
                ).valueAt(0));
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }
}
