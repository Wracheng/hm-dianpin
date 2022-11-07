package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Mr.Wang
 * @version 1.0
 * @since 1.8
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient(){
        // useSingleServer是一个redis节点 useClusterServers()是redis集群
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.203.100:6379").setPassword("123");
        return Redisson.create(config);
    }
}
