package com.wild.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wild.entity.Voucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

    @Select("select  * from tb_voucher where shop_id=#{shopId}")
    List<Voucher> queryAllById(Long shopId);
}
