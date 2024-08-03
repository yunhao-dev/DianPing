package com.wild.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.wild.dto.Result;
import com.wild.entity.Shop;
import com.wild.mapper.ShopMapper;
import com.wild.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wild.utils.CacheClient;
import com.wild.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.wild.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopMapper shopMapper;

    private BloomFilter<String> bloomFilter;
    private final ReentrantLock lock = new ReentrantLock();

    @Resource
    private CacheClient cacheClient;
    @PostConstruct
    public void initBloomFilter(){
        log.debug("布隆过滤器开始初始化....");
        List<Long> shopIds = shopMapper.queryAllShopId();
        bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), (int) (shopIds.size()*1.5));
        for (Long shopId : shopIds) {
            bloomFilter.put(shopId.toString());
        }
        log.debug("布隆过滤器初始化完成....");
    }
    public void addShop(Long id) {
        lock.lock();
        try {
            bloomFilter.put(id.toString());
        } finally {
            lock.unlock();
        }
    }
    @Override
    public Result queryShopById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if(shopId == null){
            return Result.fail("店铺Id不能为空！");
        }
        // 1.更新数据库
        shopMapper.updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shopId);
        return Result.ok();
    }

    @Override
    public Result saveShop(Shop shop) {
        shopMapper.insert(shop);
        Long shopId = shop.getId();
        addShop(shopId);
        return Result.ok(shopId);
    }

    @Scheduled(fixedRate = 1*24*60*60*1000) // 每24小时重新初始化一次布隆过滤器
    public void refreshBloomFilter() {
        lock.lock();
        try {
            initBloomFilter();
        } finally {
            lock.unlock();
        }
    }
}
