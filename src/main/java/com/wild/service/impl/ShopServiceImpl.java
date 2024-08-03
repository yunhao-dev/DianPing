package com.wild.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.wild.dto.Result;
import com.wild.entity.Shop;
import com.wild.mapper.ShopMapper;
import com.wild.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.nio.charset.Charset;
import java.util.List;
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
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("店铺不存在");
        }else{
            // 返回商铺信息
            return Result.ok(shop);
        }
    }
    public Shop queryWithMutex(Long id) {
        // 随机值解决缓存雪崩
        long ttl = CACHE_SHOP_TTL + ThreadLocalRandom.current().nextInt(-5, 6);
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
        if("null".equals(shopJson)){
            return null;
        }
        // 3.判断是否命中
        if(StrUtil.isNotBlank(shopJson)){
            // 3.1 命中
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 4.实现缓存重构
        // 4.1 获取互斥锁
        String lockey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockey);
            // 4.2 判断是否获取成功
            if(!isLock){
                // 4.3 失败，则休眠重试
                return queryWithMutex(id);
            }
            // 4.4 成功，根据id查询数据库
            shop = shopMapper.queryShopById(id);
            // 5.判断商铺是否存在
            if(shop == null){
                // 5.1.不存在，返回404
                stringRedisTemplate.opsForValue().set(shopKey,"null",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6.存在，将商铺数据写入Redis
            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),ttl, TimeUnit.MINUTES);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            // 7. 释放锁
            unLock(lockey);
        }
        // 7.返回商铺信息
        return shop;
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
