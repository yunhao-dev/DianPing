package com.wild.mapper;

import com.wild.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopMapper extends BaseMapper<Shop> {

    /**
     * 根据id查询商铺信息
     *
     * @param id
     * @return
     */
    Shop queryShopById(Long id);

    /**
     * 查询所有商铺Id
     * @return
     */
    List<Long> queryAllShopId();
}
