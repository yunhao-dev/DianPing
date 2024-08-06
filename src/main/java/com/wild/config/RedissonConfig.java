package com.wild.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @description:
 * @Author: yunhao_dev
 * @Date: 2024/8/6 10:44
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.133.100:6379")
                .setPassword("yunhao802351");
        return Redisson.create(config);
    }
}
