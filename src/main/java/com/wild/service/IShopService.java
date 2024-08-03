package com.wild.service;

import com.wild.dto.Result;
import com.wild.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    /**
     * 根据Id查询店铺信息
     * @param id
     * @return
     */
    Result queryShopById(Long id);

    /**
     * 更新店铺信息
     * @param shop
     * @return
     */
    Result update(Shop shop);

    /**
     * 保存店铺信息
     * @param shop
     * @return
     */
    Result saveShop(Shop shop);
}
