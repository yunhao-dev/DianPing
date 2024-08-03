package com.wild.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wild.dto.Result;
import com.wild.entity.Shop;
import com.wild.mapper.ShopMapper;
import com.wild.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.wild.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.wild.utils.RedisConstants.CACHE_SHOP_TTL;

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
    @Override
    public Result queryShopById(Long id) {
        // 1.获取商铺Id
        String shopKey = CACHE_SHOP_KEY + id;
        // 2.根据商铺Id从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 3.判断是否命中
        if(StrUtil.isNotBlank(shopJson)){
            // 3.1 判断是否是空值
            if (shopJson.equals("null")) {
                return Result.fail("商铺不存在！");
            }
            // 3.2 命中
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 4.没有命中，根据Id查询数据库
        Shop shop = shopMapper.queryShopById(id);
        // 5.判断商铺是否存在
        if(shop == null){
            // 5.1 存储空值解决缓存穿透
            stringRedisTemplate.opsForValue().set(shopKey,"null",CACHE_SHOP_TTL, TimeUnit.MINUTES);
            // 5.2.不存在，返回404
            return Result.fail("商铺不存在！");
        }

        // 6.存在，将商铺数据写入Redis
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回商铺信息
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
}
