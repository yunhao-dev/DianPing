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

    Result update(Shop shop);
}
