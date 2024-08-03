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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
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
        // 互斥锁缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }else{
            // 返回商铺信息
            return Result.ok(shop);
        }
    }
    public Shop queryWithLogicalExpire(Long id) {
        // 使用布隆过滤器进行初步判断
        // 如果布隆过滤器判断不存在，直接返回不存在的结果
        if (!bloomFilter.mightContain(id.toString())) {
            // 如果布隆过滤器判断不存在，直接返回不存在的结果
            return null;
        }
        // 1.获取商铺Id
        String shopKey = CACHE_SHOP_KEY + id;
        // 2.根据商铺Id从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 3.判断是否命中
        if(StrUtil.isBlank(shopJson)){
            // 3.1 没有命中
            return null;
        }
        // 4. 命中
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期，返回商铺信息
            return shop;
        }
        // 5.2 已过期，需要缓存重建
        // 6 换成重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2.判断是否获取锁成功
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{

                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }
        // 7.返回商铺信息
        return shop;
    }
    public void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop = shopMapper.queryShopById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now());
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
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
